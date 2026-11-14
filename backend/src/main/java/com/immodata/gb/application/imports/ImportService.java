package com.immodata.gb.application.imports;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.batch.imports.ImportJobFactory;
import com.immodata.gb.infrastructure.config.ImportProperties;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Démarrage et consultation des imports CSV. {@link #start} valide le fichier, écrit un fichier temporaire,
 * crée {@code import_job} (queued) puis lance le job Spring Batch en asynchrone.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final char BOM = '\uFEFF';
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel",
            "application/octet-stream");

    private final ImportRepository repository;
    private final ImportCsvValidator validator;
    private final ImportProperties properties;
    private final ImportJobFactory jobFactory;
    private final JobLauncher jobLauncher;

    public ImportService(
            ImportRepository repository,
            ImportCsvValidator validator,
            ImportProperties properties,
            ImportJobFactory jobFactory,
            JobLauncher jobLauncher) {
        this.repository = repository;
        this.validator = validator;
        this.properties = properties;
        this.jobFactory = jobFactory;
        this.jobLauncher = jobLauncher;
    }

    public ImportStartResult start(MultipartFile file) {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        if (properties.organisationId() == null) {
            log.error("app.imports.organisation-id is not configured while import mutations are enabled");
            throw new ApiException("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        validateFile(file);
        validator.assertValidHeader(readHeader(file));

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        Path tempFile = copyToTemp(file);
        UUID jobId;
        try {
            jobId = repository.insertJob(properties.organisationId(), originalFilename, tempFile.toString());
        } catch (RuntimeException e) {
            deleteQuietly(tempFile);
            throw e;
        }

        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addString("jobId", jobId.toString())
                    .addString("filePath", tempFile.toString())
                    .toJobParameters();
            jobLauncher.run(jobFactory.build(jobId, tempFile), parameters);
        } catch (Exception e) {
            log.error("Unable to launch import job {}", jobId, e);
            deleteQuietly(tempFile);
            try {
                repository.markFinished(jobId, "failed", 0, 0, 0, "Falha ao iniciar o processamento");
            } catch (RuntimeException markFailure) {
                log.error("Unable to mark import job {} as failed", jobId, markFailure);
            }
            throw new ApiException("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ImportStartResult(jobId, "queued");
    }

    public ImportJobStatus getStatus(UUID id) {
        return repository
                .findStatus(id)
                .orElseThrow(() -> new ApiException("IMPORT_JOB_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    public ImportReport getReport(UUID id) {
        return repository.findReport(id);
    }

    public PageResponse<ImportJobStatus> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPage(safePage, safeSize);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("IMPORT_FILE_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new ApiException("IMPORT_FILE_TOO_LARGE", HttpStatus.BAD_REQUEST);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ApiException("IMPORT_INVALID_TYPE", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String mime = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(mime)) {
                throw new ApiException("IMPORT_INVALID_TYPE", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private String[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) {
                return new String[0];
            }
            if (!line.isEmpty() && line.charAt(0) == BOM) {
                line = line.substring(1);
            }
            return new DelimitedLineTokenizer(",").tokenize(line).getValues();
        } catch (IOException e) {
            log.error("Unable to read import file header", e);
            throw new ApiException("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Path copyToTemp(MultipartFile file) {
        Path tempFile = null;
        try (InputStream in = file.getInputStream()) {
            tempFile = Files.createTempFile("immodata-import-", ".csv");
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (IOException e) {
            log.error("Unable to store import file", e);
            deleteQuietly(tempFile);
            throw new ApiException("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static String sanitizeFilename(String raw) {
        String name = raw == null ? "import.csv" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            name = name.substring(name.length() - MAX_FILENAME_LENGTH);
        }
        return name;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Unable to delete temporary import file {}", path.getFileName(), e);
        }
    }
}
