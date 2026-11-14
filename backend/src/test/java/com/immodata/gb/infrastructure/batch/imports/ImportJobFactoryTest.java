package com.immodata.gb.infrastructure.batch.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.immodata.gb.application.imports.ImportCsvValidator;
import com.immodata.gb.domain.imports.ImportCsvRow;
import com.immodata.gb.infrastructure.config.ImportProperties;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.dao.DuplicateKeyException;

/** Exécute le vrai job (reader/processor/writer/listener) sans base ni tables BATCH_*. */
class ImportJobFactoryTest {

    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final String HEADER = "reference,title,property_type,address_text,latitude,longitude\n";

    @TempDir
    Path dir;

    private final ImportRepository repository = mock(ImportRepository.class);

    private JobExecution run(UUID jobId, Path file) throws Exception {
        InMemoryJobRepository jobRepository = new InMemoryJobRepository();
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SyncTaskExecutor());
        launcher.afterPropertiesSet();
        ImportJobFactory factory = new ImportJobFactory(
                jobRepository, repository, new ImportCsvValidator(), new ImportProperties(true, ORG, 1024));
        JobParameters parameters = new JobParametersBuilder()
                .addString("jobId", jobId.toString())
                .addString("filePath", file.toString())
                .toJobParameters();
        return launcher.run(factory.build(jobId, file), parameters);
    }

    private Path csv(String body) throws Exception {
        Path file = dir.resolve("in.csv");
        Files.writeString(file, HEADER + body, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void mixedFileImportsValidRowAndReportsErrors() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(repository.existsActiveReference("BIS-001")).thenReturn(true);
        Path file = csv("""
                NEW-1,Casa nova,house,Rua A,11.85,-15.59
                NEW-2,Tipo mau,castle,,11.85,-15.59
                BIS-001,Ja existe,land,,11.85,-15.59
                """);

        JobExecution execution = run(jobId, file);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        ArgumentCaptor<ImportCsvRow> row = ArgumentCaptor.forClass(ImportCsvRow.class);
        verify(repository).insertPropertyBundle(eq(ORG), row.capture());
        assertThat(row.getValue().reference()).isEqualTo("NEW-1");
        assertThat(row.getValue().rowNumber()).isEqualTo(1);
        verify(repository).insertRowError(eq(jobId), eq(2), eq("IMPORT_INVALID_PROPERTY_TYPE"), anyString(), anyString());
        verify(repository).insertRowError(eq(jobId), eq(3), eq("IMPORT_DUPLICATE_REFERENCE"), anyString(), anyString());
        verify(repository).markProcessing(jobId);
        verify(repository).markFinished(jobId, "completed", 3, 1, 2, null);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void hashPrefixedDataRowIsProcessedAndCountedNotTreatedAsComment() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path file = csv("""
                OK-1,Casa,house,,11.85,-15.59
                #HASH-1,Casa cerca,house,,11.85,-15.59
                OK-2,Outra,land,,11.86,-15.60
                """);

        JobExecution execution = run(jobId, file);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        ArgumentCaptor<ImportCsvRow> rows = ArgumentCaptor.forClass(ImportCsvRow.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).insertPropertyBundle(eq(ORG), rows.capture());
        // La ligne 2 (#HASH-1) doit être soit importée, soit signalée en erreur, jamais ignorée.
        boolean imported = rows.getAllValues().stream().anyMatch(r -> r.rowNumber() == 2);
        if (!imported) {
            verify(repository).insertRowError(eq(jobId), eq(2), anyString(), anyString(), anyString());
        }
        verify(repository).markFinished(eq(jobId), eq("completed"), eq(3), any(Integer.class), any(Integer.class), isNull());
    }

    @Test
    void duplicateReferenceWithinSameFileIsRejected() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path file = csv("""
                DUP-1,Primeira,house,,11.85,-15.59
                DUP-1,Segunda,house,,11.86,-15.60
                """);

        run(jobId, file);

        verify(repository).insertPropertyBundle(eq(ORG), any(ImportCsvRow.class));
        verify(repository).insertRowError(eq(jobId), eq(2), eq("IMPORT_DUPLICATE_REFERENCE"), anyString(), anyString());
        verify(repository).markFinished(jobId, "completed", 2, 1, 1, null);
    }

    @Test
    void uniqueConstraintRaceBecomesDuplicateRowError() throws Exception {
        UUID jobId = UUID.randomUUID();
        doThrow(new DuplicateKeyException("uidx")).when(repository).insertPropertyBundle(eq(ORG), any());
        Path file = csv("RACE-1,Casa,house,,11.85,-15.59\n");

        JobExecution execution = run(jobId, file);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(repository).insertRowError(eq(jobId), eq(1), eq("IMPORT_DUPLICATE_REFERENCE"), anyString(), anyString());
        verify(repository).markFinished(jobId, "completed", 1, 0, 1, null);
    }

    @Test
    void unexpectedWriterFailureMarksJobFailedAndStillDeletesFile() throws Exception {
        UUID jobId = UUID.randomUUID();
        doThrow(new IllegalStateException("db down")).when(repository).insertPropertyBundle(eq(ORG), any());
        Path file = csv("OK-1,Casa,house,,11.85,-15.59\n");

        JobExecution execution = run(jobId, file);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(repository).markFinished(eq(jobId), eq("failed"), any(Integer.class), eq(0), eq(0), message.capture());
        assertThat(message.getValue()).doesNotContain("db down");
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void missingFileFailsJob() throws Exception {
        UUID jobId = UUID.randomUUID();

        JobExecution execution = run(jobId, dir.resolve("absent.csv"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        verify(repository).markFinished(eq(jobId), eq("failed"), eq(0), eq(0), eq(0), anyString());
        verify(repository, never()).insertPropertyBundle(any(), any());
        verify(repository, never()).markFinished(any(), eq("completed"), any(Integer.class), any(Integer.class), any(Integer.class), isNull());
    }
}
