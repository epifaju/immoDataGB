package com.immodata.gb.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.admin.*} : mutations désactivées par défaut ; vraies dans le profil {@code dev}. */
@ConfigurationProperties("app.admin")
public record AdminProperties(
        @DefaultValue("false") boolean mutationsEnabled) {
}
