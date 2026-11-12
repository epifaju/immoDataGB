package com.immodata.gb.api.confidence;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.common.UuidParam;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.confidence.ConfidenceBatchResult;
import com.immodata.gb.application.confidence.ConfidenceEngineService;
import com.immodata.gb.application.confidence.ConfidenceResult;
import com.immodata.gb.infrastructure.config.ConfidenceProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfidenceController {
    private final ConfidenceEngineService engineService;
    private final ConfidenceProperties properties;

    public ConfidenceController(ConfidenceEngineService engineService, ConfidenceProperties properties) {
        this.engineService = engineService;
        this.properties = properties;
    }

    @PostMapping("/api/v1/properties/{id}/confidence/recalculate")
    public ResponseEntity<ApiResponse<ConfidenceResult>> recalculateProperty(@PathVariable("id") String id) {
        requireEnabled();
        UUID uuid = UuidParam.parse(id, "PROPERTY_ID_INVALID");
        ConfidenceResult result = engineService.recalculateProperty(uuid);
        return ResponseEntity.ok().body(new ApiResponse<>(true, result, null, Map.of()));
    }

    @PostMapping("/api/v1/organisations/{orgId}/confidence/recalculate")
    public ResponseEntity<ApiResponse<ConfidenceBatchResult>> recalculateOrganisation(
            @PathVariable("orgId") String orgId) {
        requireEnabled();
        UUID uuid = UuidParam.parse(orgId, "ORGANISATION_ID_INVALID");
        ConfidenceBatchResult result = engineService.recalculateOrganisation(uuid);
        return ResponseEntity.ok().body(new ApiResponse<>(true, result, null, Map.of()));
    }

    private void requireEnabled() {
        if (!properties.recalculationEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
    }
}
