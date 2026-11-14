package com.immodata.gb.infrastructure.batch.imports;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;

/**
 * {@link JobRepository} en mémoire (Spring Batch 5 n'a plus de {@code MapJobRepository}) : aucune table
 * {@code BATCH_*}. L'état durable d'un import est dans {@code import_job} / {@code import_row_error}.
 *
 * <p>Chaque lancement crée une nouvelle instance de job ; il n'y a ni redémarrage ni historique : les
 * exécutions ne sont pas conservées après leur fin (pas de fuite mémoire).
 */
public class InMemoryJobRepository implements JobRepository {

    private final AtomicLong instanceIds = new AtomicLong();
    private final AtomicLong jobExecutionIds = new AtomicLong();
    private final AtomicLong stepExecutionIds = new AtomicLong();

    @Override
    public boolean isJobInstanceExists(String jobName, JobParameters jobParameters) {
        return false;
    }

    @Override
    public JobInstance createJobInstance(String jobName, JobParameters jobParameters) {
        return new JobInstance(instanceIds.incrementAndGet(), jobName);
    }

    @Override
    public JobExecution createJobExecution(String jobName, JobParameters jobParameters) {
        JobInstance instance = createJobInstance(jobName, jobParameters);
        JobExecution execution = new JobExecution(instance, jobExecutionIds.incrementAndGet(), jobParameters);
        execution.setCreateTime(LocalDateTime.now());
        return execution;
    }

    @Override
    public void update(JobExecution jobExecution) {
        jobExecution.setLastUpdated(LocalDateTime.now());
    }

    @Override
    public void add(StepExecution stepExecution) {
        stepExecution.setId(stepExecutionIds.incrementAndGet());
        stepExecution.setCreateTime(LocalDateTime.now());
    }

    @Override
    public void addAll(Collection<StepExecution> stepExecutions) {
        stepExecutions.forEach(this::add);
    }

    @Override
    public void update(StepExecution stepExecution) {
        stepExecution.setLastUpdated(LocalDateTime.now());
    }

    @Override
    public void updateExecutionContext(StepExecution stepExecution) {
        // état en mémoire uniquement
    }

    @Override
    public void updateExecutionContext(JobExecution jobExecution) {
        // état en mémoire uniquement
    }

    @Override
    public StepExecution getLastStepExecution(JobInstance jobInstance, String stepName) {
        return null;
    }

    @Override
    public long getStepExecutionCount(JobInstance jobInstance, String stepName) {
        return 0;
    }

    @Override
    public JobExecution getLastJobExecution(String jobName, JobParameters jobParameters) {
        return null;
    }
}
