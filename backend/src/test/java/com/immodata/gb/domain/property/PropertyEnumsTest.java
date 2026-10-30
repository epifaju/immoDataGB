package com.immodata.gb.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropertyEnumsTest {

    @Test
    void docStatusRoundTripsAllValues() {
        for (DocStatus status : DocStatus.values()) {
            assertEquals(status, DocStatus.fromSql(status.getSqlValue()).orElseThrow());
        }
        assertEquals(5, DocStatus.values().length);
        assertTrue(DocStatus.fromSql("unknown").isEmpty());
    }

    @Test
    void sourceTypeRoundTripsAllValues() {
        for (SourceType type : SourceType.values()) {
            assertEquals(type, SourceType.fromSql(type.getSqlValue()).orElseThrow());
        }
        assertEquals(7, SourceType.values().length);
        assertTrue(SourceType.fromSql("unknown").isEmpty());
    }
}
