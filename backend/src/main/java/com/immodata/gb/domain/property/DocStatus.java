package com.immodata.gb.domain.property;

import java.util.Arrays;
import java.util.Optional;

public enum DocStatus {
    NONE("none"),
    DECLARED("declared"),
    PARTIAL("partial"),
    VERIFIED("verified"),
    LITIGIOUS("litigious");

    private final String sqlValue;

    DocStatus(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    public String getSqlValue() {
        return sqlValue;
    }

    public static Optional<DocStatus> fromSql(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.sqlValue.equals(raw))
                .findFirst();
    }
}
