package com.immodata.gb.application.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.batch.imports.ImportJobFactory;
import com.immodata.gb.infrastructure.config.ImportProperties;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class ImportServiceTest {

    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final String HEADER = "reference,title,property_type,address_text,latitude,longitude";

    private final ImportRepository repository = mock(ImportRepository.class);
    private final ImportJobFactory jobFactory = mock(ImportJobFactory.class);
    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final ImportCsvValidator validator = new ImportCsvValidator();

    private ImportService service(boolean enabled, UUID org, long maxBytes) {
        return new ImportService(
                repository, validator, new ImportProperties(enabled, org, maxBytes), jobFactory, jobLauncher);
    }

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mutationsDisabledReturnsNotFoundBeforeAnyValidation() {
        ImportService service = service(false, ORG, 1024);

        assertThatThrownBy(() -> service.start(null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verifyNoInteractions(repository, jobLauncher, jobFactory);
    }

    @Test
    void missingOrganisationIdIsInternalError() {
        ImportService service = service(true, null, 1024);

        assertThatThrownBy(() -> service.start(csv("a.csv", HEADER + "\n")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INTERNAL_ERROR");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        verifyNoInteractions(repository, jobLauncher);
    }

    @Test
    void missingFileIsRejected() {
        ImportService service = service(true, ORG, 1024);

        assertThatThrownBy(() -> service.start(null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode())
                        .isEqualTo("IMPORT_FILE_REQUIRED"));
        assertThatThrownBy(() -> service.start(csv("a.csv", "")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode())
                        .isEqualTo("IMPORT_FILE_REQUIRED"));
    }

    @Test
    void oversizedFileIsRejected() {
        ImportService service = service(true, ORG, 10);

        assertThatThrownBy(() -> service.start(csv("a.csv", HEADER + "\n")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode())
                        .isEqualTo("IMPORT_FILE_TOO_LARGE"));
        verifyNoInteractions(repository, jobLauncher);
    }

    @Test
    void nonCsvIsRejected() {
        ImportService service = service(true, ORG, 1024);

        assertThatThrownBy(() -> service.start(csv("a.xlsx", HEADER + "\n")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode())
                        .isEqualTo("IMPORT_INVALID_TYPE"));
        MockMultipartFile wrongMime = new MockMultipartFile(
                "file", "a.csv", "application/pdf", (HEADER + "\n").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.start(wrongMime))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode())
                        .isEqualTo("IMPORT_INVALID_TYPE"));
    }

    @Test
    void invalidHeaderIsRejectedWithoutCreatingJob() {
        ImportService service = service(true, ORG, 1024);

        assertThatThrownBy(() -> service.start(csv("a.csv", "ref,title\nx,y\n")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("IMPORT_INVALID_HEADER");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(repository, never()).insertJob(any(), anyString(), anyString());
        verifyNoInteractions(jobLauncher);
    }

    @Test
    void validFileCreatesQueuedJobAndLaunchesBatch() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(repository.insertJob(eq(ORG), eq("lote.csv"), anyString())).thenReturn(jobId);
        Job job = mock(Job.class);
        when(jobFactory.build(eq(jobId), any(Path.class))).thenReturn(job);
        ImportService service = service(true, ORG, 1024);

        // BOM UTF-8 (Excel) toléré sur l'en-tête.
        ImportStartResult result = service.start(csv("lote.csv", "\uFEFF" + HEADER + "\nA-1,T,house,,11.8,-15.6\n"));

        assertThat(result).isEqualTo(new ImportStartResult(jobId, "queued"));
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(repository).insertJob(eq(ORG), eq("lote.csv"), path.capture());
        ArgumentCaptor<JobParameters> params = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(job), params.capture());
        assertThat(params.getValue().getString("jobId")).isEqualTo(jobId.toString());
        assertThat(params.getValue().getString("filePath")).isEqualTo(path.getValue());
        assertThat(Files.readString(Path.of(path.getValue()))).contains("A-1,T,house");
        Files.deleteIfExists(Path.of(path.getValue()));
    }

    @Test
    void launchFailureMarksJobFailedAndCleansTempFile() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(repository.insertJob(eq(ORG), anyString(), anyString())).thenReturn(jobId);
        when(jobFactory.build(eq(jobId), any(Path.class))).thenReturn(mock(Job.class));
        when(jobLauncher.run(any(), any())).thenThrow(new IllegalStateException("boom"));
        ImportService service = service(true, ORG, 1024);

        assertThatThrownBy(() -> service.start(csv("lote.csv", HEADER + "\nA-1,T,house,,11.8,-15.6\n")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode())
                        .isEqualTo("INTERNAL_ERROR"));

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(repository).insertJob(eq(ORG), anyString(), path.capture());
        verify(repository).markFinished(eq(jobId), eq("failed"), eq(0), eq(0), eq(0), anyString());
        assertThat(Files.exists(Path.of(path.getValue()))).isFalse();
    }

    @Test
    void getStatusUnknownJobIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findStatus(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true, ORG, 1024).getStatus(id))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("IMPORT_JOB_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void listClampsPaging() {
        service(false, ORG, 1024).list(-3, 1000);
        verify(repository).findPage(0, 100);
        service(false, ORG, 1024).list(2, 0);
        verify(repository).findPage(2, 20);
        verify(repository, never()).findPage(anyInt(), eq(1000));
    }
}
