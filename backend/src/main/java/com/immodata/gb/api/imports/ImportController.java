package com.immodata.gb.api.imports;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.common.UuidParam;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.imports.ImportJobStatus;
import com.immodata.gb.application.imports.ImportReport;
import com.immodata.gb.application.imports.ImportService;
import com.immodata.gb.application.imports.ImportStartResult;
import com.immodata.gb.infrastructure.config.ImportProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Imports CSV : démarrage asynchrone (202), statut, rapport et liste paginée. */
@RestController
public class ImportController {

    private static final String JOB_ID_INVALID = "IMPORT_JOB_ID_INVALID";
    private static final String PAGE_INVALID = "IMPORT_PAGE_INVALID";

    private final ImportService service;
    private final ImportProperties properties;

    public ImportController(ImportService service, ImportProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping(value = "/api/v1/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportStartResult>> start(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException("IMPORT_FILE_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        ImportStartResult result = service.start(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(true, result, null, Map.of()));
    }

    @GetMapping("/api/v1/imports/{id}/status")
    public ResponseEntity<ApiResponse<ImportJobStatus>> status(@PathVariable("id") String id) {
        UUID jobId = UuidParam.parse(id, JOB_ID_INVALID);
        return ResponseEntity.ok(new ApiResponse<>(true, service.getStatus(jobId), null, Map.of()));
    }

    @GetMapping("/api/v1/imports/{id}/report")
    public ResponseEntity<ApiResponse<ImportReport>> report(@PathVariable("id") String id) {
        UUID jobId = UuidParam.parse(id, JOB_ID_INVALID);
        return ResponseEntity.ok(new ApiResponse<>(true, service.getReport(jobId), null, Map.of()));
    }

    @GetMapping("/api/v1/imports")
    public ResponseEntity<ApiResponse<PageResponse<ImportJobStatus>>> list(
            @RequestParam(value = "page", defaultValue = "0") String page,
            @RequestParam(value = "size", defaultValue = "20") String size) {
        PageResponse<ImportJobStatus> result = service.list(parseInt(page), parseInt(size));
        return ResponseEntity.ok(new ApiResponse<>(true, result, null, Map.of()));
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(PAGE_INVALID, HttpStatus.BAD_REQUEST);
        }
    }
}
