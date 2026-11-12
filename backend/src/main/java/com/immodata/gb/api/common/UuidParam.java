package com.immodata.gb.api.common;

import com.immodata.gb.api.error.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class UuidParam {
    private UuidParam() {}

    public static UUID parse(String raw, String invalidCode) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(invalidCode, HttpStatus.BAD_REQUEST);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(invalidCode, HttpStatus.BAD_REQUEST);
        }
    }
}
