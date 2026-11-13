package com.immodata.gb.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.storage.*} : endpoint et identifiants S3/MinIO. Aucun secret par défaut. */
@ConfigurationProperties("app.storage")
public record StorageProperties(
        String endpoint, String accessKey, String secretKey, @DefaultValue("us-east-1") String region) {
}
