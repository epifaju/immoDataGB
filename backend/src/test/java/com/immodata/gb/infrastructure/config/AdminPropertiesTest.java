package com.immodata.gb.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AdminPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsAreSafe() {
        runner.run(ctx -> {
            AdminProperties props = ctx.getBean(AdminProperties.class);
            assertFalse(props.mutationsEnabled());
        });
    }

    @Test
    void bindsFromProperties() {
        runner.withPropertyValues("app.admin.mutations-enabled=true")
                .run(ctx -> {
                    AdminProperties props = ctx.getBean(AdminProperties.class);
                    assertTrue(props.mutationsEnabled());
                });
    }
}
