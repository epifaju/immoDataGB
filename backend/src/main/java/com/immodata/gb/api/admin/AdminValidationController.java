package com.immodata.gb.api.admin;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.common.UuidParam;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.admin.AdminValidationService;
import com.immodata.gb.application.admin.RejectResult;
import com.immodata.gb.application.admin.ValidationQueueItem;
import com.immodata.gb.infrastructure.config.AdminProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** File de validation : lecture ouverte ; validate et reject exigent le drapeau mutations. */
@RestController
public class AdminValidationController {

    private static final String REQUEST_INVALID = "ADMIN_REQUEST_INVALID";
    private static final String PROPERTY_ID_INVALID = "PROPERTY_ID_INVALID";

    private final AdminValidationService service;
    private final AdminProperties properties;

    public AdminValidationController(AdminValidationService service, AdminProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/api/v1/admin/validation-queue")
    public ResponseEntity<ApiResponse<PageResponse<ValidationQueueItem>>> queue(
            @RequestParam(value = "page", defaultValue = "0") String page,
            @RequestParam(value = "size", defaultValue = "20") String size) {
        PageResponse<ValidationQueueItem> result = service.listQueue(parseInt(page), parseInt(size));
        return ResponseEntity.ok(new ApiResponse<>(true, result, null, Map.of()));
    }

    @PostMapping("/api/v1/admin/properties/{id}/validate")
    public ResponseEntity<ApiResponse<ValidationQueueItem>> validate(@PathVariable("id") String id) {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        UUID propertyId = UuidParam.parse(id, PROPERTY_ID_INVALID);
        return ResponseEntity.ok(new ApiResponse<>(true, service.validate(propertyId), null, Map.of()));
    }

    @PostMapping("/api/v1/admin/properties/{id}/reject")
    public ResponseEntity<ApiResponse<RejectResult>> reject(@PathVariable("id") String id) {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        UUID propertyId = UuidParam.parse(id, PROPERTY_ID_INVALID);
        return ResponseEntity.ok(new ApiResponse<>(true, service.reject(propertyId), null, Map.of()));
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(REQUEST_INVALID, HttpStatus.BAD_REQUEST);
        }
    }
}
