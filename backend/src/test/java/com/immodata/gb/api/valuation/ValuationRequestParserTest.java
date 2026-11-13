package com.immodata.gb.api.valuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.valuation.EstimateRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ValuationRequestParserTest {

    private static Map<String, Object> valid() {
        Map<String, Object> m = new HashMap<>();
        m.put("propertyType", "apartment");
        m.put("areaSqm", 80);
        m.put("latitude", 11.863);
        m.put("longitude", -15.598);
        m.put("radiusMeters", 5000);
        m.put("periodMonths", 24);
        return m;
    }

    private static void assertInvalid(String key, Object value) {
        Map<String, Object> m = valid();
        if (value == null) {
            m.remove(key);
        } else {
            m.put(key, value);
        }
        ApiException ex = assertThrows(ApiException.class, () -> ValuationRequestParser.parse(m), key + "=" + value);
        assertEquals("VALUATION_REQUEST_INVALID", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void parsesValidBody() {
        EstimateRequest r = ValuationRequestParser.parse(valid());
        assertEquals("apartment", r.propertyType());
        assertEquals(0, new BigDecimal("80").compareTo(r.areaSqm()));
        assertEquals(11.863, r.latitude());
        assertEquals(-15.598, r.longitude());
        assertEquals(5000, r.radiusMeters());
        assertEquals(24, r.periodMonths());
    }

    @Test
    void acceptsBoundaryValues() {
        Map<String, Object> m = valid();
        m.put("areaSqm", 1_000_000);
        m.put("latitude", -90);
        m.put("longitude", 180);
        m.put("radiusMeters", 100);
        m.put("periodMonths", 120);
        EstimateRequest r = ValuationRequestParser.parse(m);
        assertEquals(100, r.radiusMeters());
        assertEquals(120, r.periodMonths());
    }

    @Test
    void rejectsNullBody() {
        assertThrows(ApiException.class, () -> ValuationRequestParser.parse(null));
    }

    @Test
    void rejectsInvalidPropertyType() {
        assertInvalid("propertyType", "castle");
        assertInvalid("propertyType", null);
        assertInvalid("propertyType", 3);
    }

    @Test
    void rejectsInvalidArea() {
        assertInvalid("areaSqm", 0);
        assertInvalid("areaSqm", -5);
        assertInvalid("areaSqm", 1_000_001);
        assertInvalid("areaSqm", "80");
        assertInvalid("areaSqm", null);
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertInvalid("latitude", 90.1);
        assertInvalid("latitude", -90.1);
        assertInvalid("longitude", 180.1);
        assertInvalid("longitude", -180.1);
        assertInvalid("latitude", Double.NaN);
        assertInvalid("longitude", null);
    }

    @Test
    void rejectsInvalidRadius() {
        assertInvalid("radiusMeters", 99);
        assertInvalid("radiusMeters", 50_001);
        assertInvalid("radiusMeters", 500.5);
        assertInvalid("radiusMeters", "500");
    }

    @Test
    void rejectsInvalidPeriod() {
        assertInvalid("periodMonths", 0);
        assertInvalid("periodMonths", 121);
        assertInvalid("periodMonths", 12.5);
        assertInvalid("periodMonths", null);
    }
}