package com.immodata.gb.infrastructure.batch.imports;

import com.immodata.gb.application.imports.ImportCsvValidator;
import com.immodata.gb.domain.imports.ImportCsvRow;
import com.immodata.gb.infrastructure.config.ImportProperties;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Construit un job d'import par lancement (reader sur le fichier temp, processor et writer liés au
 * {@code jobId}) : pas de scope Batch ni de paramètres résolus tardivement.
 */
public class ImportJobFactory {

    public static final String JOB_NAME = "importCsvJob";
    static final int CHUNK_SIZE = 50;

    private final JobRepository jobRepository;
    private final ImportRepository repository;
    private final ImportCsvValidator validator;
    private final ImportProperties properties;
    // Les écritures métier sont transactionnelles dans ImportRepository ; le step n'a pas de ressource à enrôler.
    private final PlatformTransactionManager stepTransactionManager = new ResourcelessTransactionManager();

    public ImportJobFactory(
            JobRepository jobRepository,
            ImportRepository repository,
            ImportCsvValidator validator,
            ImportProperties properties) {
        this.jobRepository = jobRepository;
        this.repository = repository;
        this.validator = validator;
        this.properties = properties;
    }

    public Job build(UUID jobId, Path file) {
        ImportRunStats stats = new ImportRunStats();
        Step step = new StepBuilder("importCsvStep", jobRepository)
                .<ImportRawRow, ImportCsvRow>chunk(CHUNK_SIZE, stepTransactionManager)
                .reader(reader(file))
                .processor(new ImportItemProcessor(jobId, validator, repository, stats))
                .writer(new ImportItemWriter(jobId, properties.organisationId(), repository, stats))
                .build();
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(new ImportJobListener(jobId, file, repository, stats))
                .start(step)
                .build();
    }

    private static FlatFileItemReader<ImportRawRow> reader(Path file) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(",");
        return new FlatFileItemReaderBuilder<ImportRawRow>()
                .name("importCsvReader")
                .resource(new FileSystemResource(file))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .saveState(false)
                .lineMapper((line, lineNumber) -> {
                    String[] cols;
                    try {
                        cols = tokenizer.tokenize(line).getValues();
                    } catch (RuntimeException e) {
                        cols = null;
                    }
                    // lineNumber est physique (en-tête = 1) ; row_number est 1-based sur le corps.
                    return new ImportRawRow(lineNumber - 1, cols, line);
                })
                .build();
    }
}
