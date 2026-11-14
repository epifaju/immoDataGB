package com.immodata.gb.infrastructure.batch.imports;

import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;

/** {@code import_job}: queued → processing au démarrage, puis completed|failed + compteurs ; suppression du fichier temp. */
public class ImportJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ImportJobListener.class);

    static final String FAILURE_MESSAGE = "Falha ao processar o ficheiro CSV";

    private final UUID jobId;
    private final Path file;
    private final ImportRepository repository;
    private final ImportRunStats stats;

    public ImportJobListener(UUID jobId, Path file, ImportRepository repository, ImportRunStats stats) {
        this.jobId = jobId;
        this.file = file;
        this.repository = repository;
        this.stats = stats;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        repository.markProcessing(jobId);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            boolean completed = jobExecution.getStatus() == BatchStatus.COMPLETED;
            List<Throwable> failures = jobExecution.getAllFailureExceptions();
            failures.forEach(failure -> log.error("Import job {} failed", jobId, failure));
            String errorMessage = completed ? null : FAILURE_MESSAGE;
            int read = jobExecution.getStepExecutions().stream()
                    .mapToInt(step -> (int) readCount(step))
                    .sum();
            int success = stats.successRows();
            int errors = stats.errorRows();
            int total = Math.max(read, success + errors);
            repository.markFinished(jobId, completed ? "completed" : "failed", total, success, errors, errorMessage);
        } catch (RuntimeException e) {
            log.error("Unable to finalize import job {}", jobId, e);
        } finally {
            deleteTempFile();
        }
    }

    private static long readCount(StepExecution step) {
        return step.getReadCount();
    }

    private void deleteTempFile() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Unable to delete temporary import file for job {}", jobId, e);
        }
    }
}
