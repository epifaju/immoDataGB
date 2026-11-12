package com.immodata.gb.domain.confidence;

public record VerifiedSourceBonusCondition(int minVerified) implements ConfidenceCondition {

    public VerifiedSourceBonusCondition {
        if (minVerified < 1) {
            throw new IllegalArgumentException("minVerified must be >= 1");
        }
    }
}