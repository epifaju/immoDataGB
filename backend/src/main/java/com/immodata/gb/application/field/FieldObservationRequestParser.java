package com.immodata.gb.application.field;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.property.PropertyType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Validation manuelle du corps de POST /field-observations (bornes spec Bloc 6.2). */
public final class FieldObservationRequestParser {

    private static final String INVALID = "FIELD_REQUEST_INVALID";
    private static final int MAX_TITLE_LENGTH = 500;
    private static final Set<String> OCCUPATION_STATUSES = Set.of("occupied", "vacant", "unknown");

    private FieldObservationRequestParser() {}

    public static FieldObservationRequest parse(Map<String, Object> body) {
        if (body == null) {
            throw invalid();
        }
        double lat = parseCoordinate(body.get("latitude"), -90, 90);
        double lng = parseCoordinate(body.get("longitude"), -180, 180);
        String type = parseType(body.get("propertyType"));
        String title = parseOptionalText(body.get("title"));
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw invalid();
        }
        String occupation = parseOptionalText(body.get("occupationStatus"));
        if (occupation != null && !OCCUPATION_STATUSES.contains(occupation)) {
            throw invalid();
        }
        String notes = parseOptionalText(body.get("notes"));
        return new FieldObservationRequest(lat, lng, type, title, occupation, notes);
    }

    private static String parseType(Object raw) {
        if (!(raw instanceof String s)) {
            throw invalid();
        }
        return PropertyType.fromParam(s).orElseThrow(FieldObservationRequestParser::invalid).getSqlValue();
    }

    private static String parseOptionalText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String s)) {
            throw invalid();
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static double parseCoordinate(Object raw, double min, double max) {
        if (!(raw instanceof Number number)) {
            throw invalid();
        }
        double value;
        if (number instanceof BigDecimal bd) {
            value = bd.doubleValue();
        } else {
            value = number.doubleValue();
        }
        if (!Double.isFinite(value) || value < min || value > max) {
            throw invalid();
        }
        return value;
    }

    private static ApiException invalid() {
        return new ApiException(INVALID, HttpStatus.BAD_REQUEST);
    }
}
