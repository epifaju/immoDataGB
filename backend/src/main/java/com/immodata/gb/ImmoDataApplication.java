package com.immodata.gb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// BatchAutoConfiguration exclue : les imports utilisent un JobRepository en mémoire (ImportBatchConfig),
// sans tables BATCH_* ; l'auto-config enregistrerait un JobRepository JDBC.
@SpringBootApplication(exclude = BatchAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ImmoDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImmoDataApplication.class, args);
    }
}
