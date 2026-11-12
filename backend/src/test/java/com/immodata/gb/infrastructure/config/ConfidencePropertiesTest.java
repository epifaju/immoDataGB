package com.immodata.gb.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfidencePropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConfidenceProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void recalculationIsDisabledByDefault() {
        runner.run(ctx -> assertFalse(ctx.getBean(ConfidenceProperties.class).recalculationEnabled()));
    }

    @Test
    void recalculationFlagBindsFromProperty() {
        runner.withPropertyValues("app.confidence.recalculation-enabled=true")
                .run(ctx -> assertTrue(ctx.getBean(ConfidenceProperties.class).recalculationEnabled()));
    }

    @Test
    void clockBeanIsSystemUtc() {
        new ApplicationContextRunner().withUserConfiguration(ClockConfig.class).run(ctx -> {
            Clock clock = ctx.getBean(Clock.class);
            assertEquals(ZoneOffset.UTC, clock.getZone());
        });
    }
}
