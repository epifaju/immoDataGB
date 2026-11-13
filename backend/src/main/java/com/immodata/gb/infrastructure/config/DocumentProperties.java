package com.immodata.gb.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.documents.*} : mutations désactivées par défaut ; vraies dans le profil {@code dev}. */
@ConfigurationProperties("app.documents")
public record DocumentProperties(
        @DefaultValue("false") boolean mutationsEnabled,
        @DefaultValue("documents") String bucket,
        @DefaultValue("15m") Duration signedUrlTtl) {
}
