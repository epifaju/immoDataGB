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

class FieldPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FieldProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsAreSafe() {
        runner.run(ctx -> {
            FieldProperties props = ctx.getBean(FieldProperties.class);
            assertFalse(props.mutationsEnabled());
            assertNull(props.organisationId());
        });
    }

    @Test
    void bindsFromProperties() {
        runner.withPropertyValues(
                        "app.field.mutations-enabled=true",
                        "app.field.organisation-id=00000000-0000-0000-0000-00000000000a")
                .run(ctx -> {
                    FieldProperties props = ctx.getBean(FieldProperties.class);
                    assertTrue(props.mutationsEnabled());
                    assertEquals(UUID.fromString("00000000-0000-0000-0000-00000000000a"), props.organisationId());
                });
    }
}
