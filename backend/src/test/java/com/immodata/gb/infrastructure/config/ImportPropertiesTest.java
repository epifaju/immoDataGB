package com.immodata.gb.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ImportPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ImportProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsAreSafe() {
        runner.run(ctx -> {
            ImportProperties props = ctx.getBean(ImportProperties.class);
            assertFalse(props.mutationsEnabled());
            assertNull(props.organisationId());
            assertEquals(5_242_880L, props.maxFileSizeBytes());
        });
    }

    @Test
    void bindsFromProperties() {
        runner.withPropertyValues(
                        "app.imports.mutations-enabled=true",
                        "app.imports.organisation-id=00000000-0000-0000-0000-00000000000a",
                        "app.imports.max-file-size-bytes=1024")
                .run(ctx -> {
                    ImportProperties props = ctx.getBean(ImportProperties.class);
                    assertTrue(props.mutationsEnabled());
                    assertEquals(UUID.fromString("00000000-0000-0000-0000-00000000000a"), props.organisationId());
                    assertEquals(1024L, props.maxFileSizeBytes());
                });
    }
}
