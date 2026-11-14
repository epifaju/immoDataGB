package com.immodata.gb.infrastructure.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.imports.*} : mutations désactivées par défaut ; vraies dans le profil {@code dev}. */
@ConfigurationProperties("app.imports")
public record ImportProperties(
        @DefaultValue("false") boolean mutationsEnabled,
        UUID organisationId,
        @DefaultValue("5242880") long maxFileSizeBytes) {
}
