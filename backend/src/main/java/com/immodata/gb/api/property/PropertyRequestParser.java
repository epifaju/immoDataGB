package com.immodata.gb.api.property;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.property.BoundingBox;
import com.immodata.gb.application.property.PropertyQuery;
import com.immodata.gb.domain.property.ConfidenceLevel;
import com.immodata.gb.domain.property.PropertyType;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

public final class PropertyRequestParser {
    private PropertyRequestParser() {}

    public static PropertyQuery parseList(MultiValueMap<String, String> params) {
        BoundingBox bbox = parseBbox(params.get("bbox"));
        PropertyType type = parseOptionalEnum(
                params.get("type"), PropertyType::fromParam, "PROPERTY_FILTER_INVALID");
        ConfidenceLevel confidence = parseOptionalEnum(
                params.get("confidence"), ConfidenceLevel::fromParam, "PROPERTY_FILTER_INVALID");
        int page = parsePage(params.get("page"));
        int size = parseSize(params.get("size"));
        return new PropertyQuery(bbox, type, confidence, page, size);
    }

    public static UUID parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException("PROPERTY_ID_INVALID", HttpStatus.BAD_REQUEST);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("PROPERTY_ID_INVALID", HttpStatus.BAD_REQUEST);
        }
    }

    private static BoundingBox parseBbox(List<String> values) {
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
        }
        if (values.size() > 1) {
            throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
        }
        String[] parts = values.getFirst().split(",", -1);
        if (parts.length != 4) {
            throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
        }
        double[] numbers = new double[4];
        for (int i = 0; i < 4; i++) {
            try {
                numbers[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException ex) {
                throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
            }
            if (!Double.isFinite(numbers[i])) {
                throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
            }
        }
        double west = numbers[0];
        double south = numbers[1];
        double east = numbers[2];
        double north = numbers[3];
        if (west < -180 || west > 180 || east < -180 || east > 180) {
            throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
        }
        if (south < -90 || south > 90 || north < -90 || north > 90) {
            throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
        }
        if (!(west < east) || !(south < north)) {
            throw new ApiException("PROPERTY_BBOX_INVALID", HttpStatus.BAD_REQUEST);
        }
        return new BoundingBox(west, south, east, north);
    }

    private static <T> T parseOptionalEnum(
            List<String> values,
            java.util.function.Function<String, java.util.Optional<T>> resolver,
            String errorCode) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new ApiException(errorCode, HttpStatus.BAD_REQUEST);
        }
        String raw = values.getFirst();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return resolver
                .apply(raw)
                .orElseThrow(() -> new ApiException(errorCode, HttpStatus.BAD_REQUEST));
    }

    private static int parsePage(List<String> values) {
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            return 0;
        }
        if (values.size() > 1) {
            throw new ApiException("PROPERTY_PAGE_INVALID", HttpStatus.BAD_REQUEST);
        }
        int page;
        try {
            page = Integer.parseInt(values.getFirst().trim());
        } catch (NumberFormatException ex) {
            throw new ApiException("PROPERTY_PAGE_INVALID", HttpStatus.BAD_REQUEST);
        }
        if (page < 0) {
            throw new ApiException("PROPERTY_PAGE_INVALID", HttpStatus.BAD_REQUEST);
        }
        return page;
    }

    private static int parseSize(List<String> values) {
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            return 20;
        }
        if (values.size() > 1) {
            throw new ApiException("PROPERTY_PAGE_INVALID", HttpStatus.BAD_REQUEST);
        }
        int size;
        try {
            size = Integer.parseInt(values.getFirst().trim());
        } catch (NumberFormatException ex) {
            throw new ApiException("PROPERTY_PAGE_INVALID", HttpStatus.BAD_REQUEST);
        }
        if (size < 1 || size > 100) {
            throw new ApiException("PROPERTY_PAGE_INVALID", HttpStatus.BAD_REQUEST);
        }
        return size;
    }
}
