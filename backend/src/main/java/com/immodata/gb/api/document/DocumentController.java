package com.immodata.gb.api.document;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.common.UuidParam;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.application.document.DocumentItem;
import com.immodata.gb.application.document.DocumentService;
import com.immodata.gb.application.document.SignedUrlResult;
import com.immodata.gb.infrastructure.config.DocumentProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {

    private final DocumentService service;
    private final DocumentProperties properties;

    public DocumentController(DocumentService service, DocumentProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/api/v1/properties/{id}/documents")
    public ResponseEntity<ApiResponse<List<DocumentItem>>> list(@PathVariable("id") String id) {
        UUID propertyId = UuidParam.parse(id, "PROPERTY_ID_INVALID");
        List<DocumentItem> items = service.list(propertyId);
        return ResponseEntity.ok(new ApiResponse<>(true, items, null, Map.of()));
    }

    @PostMapping(value = "/api/v1/properties/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentItem>> upload(
            @PathVariable("id") String id,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        requireMutationsEnabled();
        UUID propertyId = UuidParam.parse(id, "PROPERTY_ID_INVALID");
        DocumentItem item = service.upload(propertyId, documentType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, item, null, Map.of()));
    }

    @PostMapping("/api/v1/documents/{id}/signed-url")
    public ResponseEntity<ApiResponse<SignedUrlResult>> signedUrl(@PathVariable("id") String id) {
        requireMutationsEnabled();
        UUID documentId = UuidParam.parse(id, "DOCUMENT_ID_INVALID");
        SignedUrlResult result = service.signedUrl(documentId);
        return ResponseEntity.ok(new ApiResponse<>(true, result, null, Map.of()));
    }

    @DeleteMapping("/api/v1/documents/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") String id) {
        requireMutationsEnabled();
        UUID documentId = UuidParam.parse(id, "DOCUMENT_ID_INVALID");
        service.softDelete(documentId);
        return ResponseEntity.ok(new ApiResponse<>(true, null, null, Map.of()));
    }

    private void requireMutationsEnabled() {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
    }
}
