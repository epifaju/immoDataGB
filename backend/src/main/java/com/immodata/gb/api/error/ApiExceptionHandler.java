package com.immodata.gb.api.error;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.health.HealthView;
import com.immodata.gb.application.confidence.ConfidenceRuleInvalidException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> apiException(ApiException exception) {
        ApiResponse<Object> body = new ApiResponse<>(false, null, exception.getCode(), Map.of());
        return ResponseEntity.status(exception.getStatus())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> notFound(NoResourceFoundException exception) {
        ApiResponse<Object> body = new ApiResponse<>(false, null, "NOT_FOUND", Map.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        ApiResponse<Object> body = new ApiResponse<>(false, null, "METHOD_NOT_ALLOWED", Map.of());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @ExceptionHandler(ConfidenceRuleInvalidException.class)
    public ResponseEntity<ApiResponse<Object>> confidenceRuleInvalid(ConfidenceRuleInvalidException exception) {
        log.error(
                "Invalid active confidence rule id={} name={}",
                exception.getRuleId(),
                exception.getRuleName(),
                exception);
        ApiResponse<Object> body = new ApiResponse<>(false, null, "INTERNAL_ERROR", Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

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
