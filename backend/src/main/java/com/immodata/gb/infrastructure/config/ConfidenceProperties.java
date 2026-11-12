package com.immodata.gb.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.confidence.recalculation-enabled} : faux par défaut ; vrai dans le profil {@code dev}. */
@ConfigurationProperties("app.confidence")
public record ConfidenceProperties(@DefaultValue("false") boolean recalculationEnabled) {
}
