package com.immodata.gb.domain.confidence;

public record AgePenaltyCondition(int olderThanMonths) implements ConfidenceCondition {

    public AgePenaltyCondition {
        if (olderThanMonths < 1) {
            throw new IllegalArgumentException("olderThanMonths must be >= 1");
        }
    }
}