package com.immodata.gb.domain.confidence;

import java.util.Objects;

public record SourceTypeBonusCondition(String sourceType) implements ConfidenceCondition {

    public SourceTypeBonusCondition {
        Objects.requireNonNull(sourceType, "sourceType");
    }
}