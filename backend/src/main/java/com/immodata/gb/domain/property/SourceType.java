package com.immodata.gb.domain.property;

import java.util.Arrays;
import java.util.Optional;

public enum SourceType {
    AGENCY_LISTING("agency_listing"),
    FIELD_OBSERVATION("field_observation"),
    SCANNED_DOC("scanned_doc"),
    INSTITUTIONAL("institutional"),
    THIRD_PARTY_USER("third_party_user"),
    PARTNER("partner"),
    OPEN_LAYER("open_layer");

    private final String sqlValue;

    SourceType(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    public String getSqlValue() {
        return sqlValue;
    }

    public static Optional<SourceType> fromSql(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.sqlValue.equals(raw))
                .findFirst();
    }
}
