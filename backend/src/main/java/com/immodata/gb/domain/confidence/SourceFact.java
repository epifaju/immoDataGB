package com.immodata.gb.domain.confidence;

import java.time.LocalDate;
import java.util.Objects;

public record SourceFact(String sourceType, int weight, boolean verified, LocalDate observedAt) {

    public SourceFact {
        Objects.requireNonNull(sourceType, "sourceType");
    }
}