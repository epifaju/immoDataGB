package com.immodata.gb.domain.property;

import java.util.Arrays;
import java.util.Optional;

public enum ConfidenceLevel {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String sqlValue;

    ConfidenceLevel(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    public String getSqlValue() {
        return sqlValue;
    }

    /** Bandes : low 0–33, medium 34–66, high 67–100 (même découpage que le CHECK de {@code property}). */
    public static ConfidenceLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100: " + score);
        }
        if (score <= 33) {
            return LOW;
        }
        return score <= 66 ? MEDIUM : HIGH;
    }

    public static Optional<ConfidenceLevel> fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(level -> level.sqlValue.equals(raw))
                .findFirst();
    }
}
