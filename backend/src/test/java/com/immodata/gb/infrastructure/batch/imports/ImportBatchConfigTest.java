package com.immodata.gb.infrastructure.batch.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.immodata.gb.ImmoDataApplication;
import com.immodata.gb.application.imports.ImportCsvValidator;
import com.immodata.gb.infrastructure.config.ImportProperties;
import com.immodata.gb.infrastructure.persistence.imports.ImportRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ImportBatchConfigTest {

    @Test
    void wiresInMemoryBatchWithoutDataSource() {
        new ApplicationContextRunner()
                .withUserConfiguration(ImportBatchConfig.class)
                .withBean(ImportRepository.class, () -> mock(ImportRepository.class))
                .withBean(ImportCsvValidator.class, ImportCsvValidator::new)
                .withBean(ImportProperties.class, () -> new ImportProperties(true, UUID.randomUUID(), 1024))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JobRepository.class);
                    assertThat(context.getBean(JobRepository.class)).isInstanceOf(InMemoryJobRepository.class);
                    assertThat(context).hasSingleBean(JobLauncher.class);
                    assertThat(context).hasSingleBean(ImportJobFactory.class);
                });
    }

    @Test
    void applicationExcludesBatchAutoConfigurationSoNoBatchTablesAreRequired() {
        SpringBootApplication annotation = ImmoDataApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation.exclude()).contains(BatchAutoConfiguration.class);
    }
}
