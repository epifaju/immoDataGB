package com.immodata.gb.domain.property;

import java.util.Arrays;
import java.util.Optional;

public enum PropertyType {
    HOUSE("house"),
    APARTMENT("apartment"),
    LAND("land"),
    COMMERCIAL("commercial");

    private final String sqlValue;

    PropertyType(String sqlValue) {
        this.sqlValue = sqlValue;
    }

    public String getSqlValue() {
        return sqlValue;
    }

    public static Optional<PropertyType> fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.sqlValue.equals(raw))
                .findFirst();
    }
}
