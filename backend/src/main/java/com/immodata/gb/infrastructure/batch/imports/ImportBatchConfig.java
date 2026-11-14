package com.immodata.gb.infrastructure.batch.imports;

import com.immodata.gb.application.imports.ImportCsvValidator;
import com.immodata.gb.infrastructure.config.ImportProperties;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Infrastructure Spring Batch des imports, sans tables {@code BATCH_*} : repository en mémoire et
 * lancement asynchrone. {@code BatchAutoConfiguration} est exclue dans {@code ImmoDataApplication}
 * (sinon Boot enregistrerait un JobRepository JDBC).
 */
@Configuration(proxyBeanMethods = false)
public class ImportBatchConfig {

    @Bean
    public JobRepository importJobRepository() {
        return new InMemoryJobRepository();
    }

    @Bean
    public JobLauncher importJobLauncher(JobRepository importJobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(importJobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("import-batch-"));
        launcher.afterPropertiesSet();
        return launcher;
    }

    @Bean
    public ImportJobFactory importJobFactory(
            JobRepository importJobRepository,
            ImportRepository importRepository,
            ImportCsvValidator validator,
            ImportProperties properties) {
        return new ImportJobFactory(importJobRepository, importRepository, validator, properties);
    }
}
