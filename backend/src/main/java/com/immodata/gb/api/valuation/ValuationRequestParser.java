package com.immodata.gb.api.valuation;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.valuation.EstimateRequest;
import com.immodata.gb.domain.property.PropertyType;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Validation manuelle du corps de POST /valuations/estimate (bornes spec Bloc 5). */
public final class ValuationRequestParser {

    private static final String INVALID = "VALUATION_REQUEST_INVALID";
    private static final BigDecimal MAX_AREA = BigDecimal.valueOf(1_000_000);

    private ValuationRequestParser() {}

    public static EstimateRequest parse(Map<String, Object> body) {
        if (body == null) {
            throw invalid();
        }
        String type = parseType(body.get("propertyType"));
        BigDecimal area = parseArea(body.get("areaSqm"));
        double lat = parseDouble(body.get("latitude"), -90, 90);
        double lng = parseDouble(body.get("longitude"), -180, 180);
        int radius = parseInt(body.get("radiusMeters"), 100, 50_000);
        int period = parseInt(body.get("periodMonths"), 1, 120);
        return new EstimateRequest(type, area, lat, lng, radius, period);
    }

    private static String parseType(Object raw) {
        if (!(raw instanceof String s)) {
            throw invalid();
        }
        return PropertyType.fromParam(s).orElseThrow(ValuationRequestParser::invalid).getSqlValue();
    }

    private static BigDecimal parseArea(Object raw) {
        BigDecimal area = toBigDecimal(raw);
        if (area.signum() <= 0 || area.compareTo(MAX_AREA) > 0) {
            throw invalid();
        }
        return area;
    }

    private static double parseDouble(Object raw, double min, double max) {
        double value = toBigDecimal(raw).doubleValue();
        if (!Double.isFinite(value) || value < min || value > max) {
            throw invalid();
        }
        return value;
    }

    private static int parseInt(Object raw, int min, int max) {
        BigDecimal value = toBigDecimal(raw);
        if (value.stripTrailingZeros().scale() > 0) {
            throw invalid();
        }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw invalid();
        }
        return value.intValueExact();
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (!(raw instanceof Number number)) {
            throw invalid();
        }
        if (number instanceof BigDecimal bd) {
            return bd;
        }
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                throw invalid();
            }
            return BigDecimal.valueOf(d);
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(INVALID, HttpStatus.BAD_REQUEST);
    }
}