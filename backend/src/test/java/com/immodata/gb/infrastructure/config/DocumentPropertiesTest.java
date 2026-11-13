package com.immodata.gb.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DocumentPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DocumentProperties.class, StorageProperties.class})
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsAreSafe() {
        runner.run(ctx -> {
            DocumentProperties props = ctx.getBean(DocumentProperties.class);
            assertFalse(props.mutationsEnabled());
            assertEquals("documents", props.bucket());
            assertEquals(Duration.ofMinutes(15), props.signedUrlTtl());
        });
    }

    @Test
    void bindsFromProperties() {
        runner.withPropertyValues(
                        "app.documents.mutations-enabled=true",
                        "app.documents.bucket=other",
                        "app.documents.signed-url-ttl=5m")
                .run(ctx -> {
                    DocumentProperties props = ctx.getBean(DocumentProperties.class);
                    assertTrue(props.mutationsEnabled());
                    assertEquals("other", props.bucket());
                    assertEquals(Duration.ofMinutes(5), props.signedUrlTtl());
                });
    }

    @Test
    void storagePropertiesHaveNoSecretDefaults() {
        runner.run(ctx -> {
            StorageProperties props = ctx.getBean(StorageProperties.class);
            assertEquals(null, props.endpoint());
            assertEquals(null, props.accessKey());
            assertEquals(null, props.secretKey());
            assertEquals("us-east-1", props.region());
        });
    }

    @Test
    void storagePropertiesBindFromProperties() {
        runner.withPropertyValues(
                        "app.storage.endpoint=http://127.0.0.1:9000",
                        "app.storage.access-key=ak",
                        "app.storage.secret-key=sk",
                        "app.storage.region=eu-west-1")
                .run(ctx -> {
                    StorageProperties props = ctx.getBean(StorageProperties.class);
                    assertEquals("http://127.0.0.1:9000", props.endpoint());
                    assertEquals("ak", props.accessKey());
                    assertEquals("sk", props.secretKey());
                    assertEquals("eu-west-1", props.region());
                });
    }
}
