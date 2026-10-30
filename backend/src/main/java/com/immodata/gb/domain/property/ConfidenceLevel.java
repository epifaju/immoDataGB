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

    public static Optional<ConfidenceLevel> fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(level -> level.sqlValue.equals(raw))
                .findFirst();
    }
}
