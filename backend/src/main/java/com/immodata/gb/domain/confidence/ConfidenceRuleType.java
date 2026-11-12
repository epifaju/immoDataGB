package com.immodata.gb.domain.confidence;

import java.util.Arrays;
import java.util.Optional;

public enum ConfidenceRuleType {
    SOURCE_COUNT("source_count"),
    SOURCE_TYPE_BONUS("source_type_bonus"),
    VERIFIED_SOURCE_BONUS("verified_source_bonus"),
    DOC_STATUS_BONUS("doc_status_bonus"),
    AGE_PENALTY("age_penalty"),
    INCONSISTENCY_PENALTY("inconsistency_penalty");

    private final String sqlValue;

    ConfidenceRuleType(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    public String getSqlValue() {
        return sqlValue;
    }

    public static Optional<ConfidenceRuleType> fromSql(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.sqlValue.equals(raw))
                .findFirst();
    }
}