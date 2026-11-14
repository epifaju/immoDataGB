package com.immodata.gb.api.field;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.field.FieldObservationCreated;
import com.immodata.gb.application.field.FieldObservationRequestParser;
import com.immodata.gb.application.field.FieldObservationService;
import com.immodata.gb.infrastructure.config.FieldProperties;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Saisie terrain : création d'une observation {@code pending_validation} (201). */
@RestController
public class FieldObservationController {

    private static final String INVALID = "FIELD_REQUEST_INVALID";

    private final FieldObservationService service;
    private final FieldProperties properties;

    public FieldObservationController(FieldObservationService service, FieldProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/api/v1/field-observations")
    public ResponseEntity<ApiResponse<FieldObservationCreated>> create(@RequestBody Map<String, Object> body) {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        FieldObservationCreated created = service.create(FieldObservationRequestParser.parse(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, created, null, Map.of()));
    }

    /** JSON absent, malformé ou non-objet : même code métier que les bornes invalides. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> unreadableBody(HttpMessageNotReadableException exception) {
        ApiException invalid = new ApiException(INVALID, HttpStatus.BAD_REQUEST);
        ApiResponse<Object> body = new ApiResponse<>(false, null, invalid.getCode(), Map.of());
        return ResponseEntity.status(invalid.getStatus())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
