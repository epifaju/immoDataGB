package com.immodata.gb.infrastructure.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.field.*} : mutations désactivées par défaut ; vraies dans le profil {@code dev}. */
@ConfigurationProperties("app.field")
public record FieldProperties(
        @DefaultValue("false") boolean mutationsEnabled,
        UUID organisationId) {
}
