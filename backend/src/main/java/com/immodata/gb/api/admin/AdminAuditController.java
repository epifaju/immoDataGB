package com.immodata.gb.api.admin;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.admin.AdminValidationService;
import com.immodata.gb.application.admin.AuditEventView;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Journal d'audit : lecture ouverte, filtres optionnels. */
@RestController
public class AdminAuditController {

    private static final String REQUEST_INVALID = "ADMIN_REQUEST_INVALID";

    private final AdminValidationService service;

    public AdminAuditController(AdminValidationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/audit")
    public ResponseEntity<ApiResponse<PageResponse<AuditEventView>>> list(
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") String page,
            @RequestParam(value = "size", defaultValue = "20") String size) {
        PageResponse<AuditEventView> result = service.listAudit(
                entityType, action, parseInstant(from), parseInstant(to), parseInt(page), parseInt(size));
        return ResponseEntity.ok(new ApiResponse<>(true, result, null, Map.of()));
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(REQUEST_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(REQUEST_INVALID, HttpStatus.BAD_REQUEST);
        }
    }
}
