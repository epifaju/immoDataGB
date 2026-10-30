package com.immodata.gb.api.error;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.health.HealthView;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<HealthView>> databaseUnavailable(DataAccessException exception) {
        log.warn("PostGIS health probe failed", exception);
        ApiResponse<HealthView> body = new ApiResponse<>(
                false,
                new HealthView("up", "down", null),
                "HEALTH_DATABASE_UNAVAILABLE",
                Map.of());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<HealthView>> internalError(Exception exception) {
        log.error("Unhandled API error", exception);
        ApiResponse<HealthView> body = new ApiResponse<>(false, null, "INTERNAL_ERROR", Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
