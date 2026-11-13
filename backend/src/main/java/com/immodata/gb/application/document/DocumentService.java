package com.immodata.gb.application.document;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.document.DocumentType;
import com.immodata.gb.infrastructure.config.DocumentProperties;
import com.immodata.gb.infrastructure.persistence.document.DocumentRepository;
import com.immodata.gb.infrastructure.persistence.document.DocumentRepository.DocumentRow;
import com.immodata.gb.infrastructure.storage.DocumentStorage;
import com.immodata.gb.infrastructure.storage.StorageKeyFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestration des documents d'un bien : liste, upload (INSERT puis PUT objet), URL signée courte,
 * suppression logique. Les métadonnées sont insérées avant l'écriture objet : un échec de stockage
 * lève {@code DOCUMENT_STORAGE_UNAVAILABLE} et la transaction annule l'INSERT.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final DocumentFileValidator validator;
    private final StorageKeyFactory keyFactory;
    private final DocumentStorage storage;
    private final DocumentProperties properties;
    private final Clock clock;

    public DocumentService(
            DocumentRepository repository,
            DocumentFileValidator validator,
            StorageKeyFactory keyFactory,
            DocumentStorage storage,
            DocumentProperties properties,
            Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.keyFactory = keyFactory;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DocumentItem> list(UUID propertyId) {
        if (!repository.propertyExistsActiveWithGeometry(propertyId)) {
            throw propertyNotFound();
        }
        return repository.listActiveByProperty(propertyId);
    }

    @Transactional
    public DocumentItem upload(UUID propertyId, String documentTypeRaw, MultipartFile file) {
        DocumentType type = DocumentType.fromApi(documentTypeRaw)
                .orElseThrow(() -> new ApiException("DOCUMENT_TYPE_INVALID", HttpStatus.BAD_REQUEST));
        ValidatedFile validated = validator.validate(file);
        UUID organisationId = repository
                .findOrganisationIdForActiveProperty(propertyId)
                .orElseThrow(DocumentService::propertyNotFound);

        UUID documentId = UUID.randomUUID();
        String bucket = properties.bucket();
        String key = keyFactory.build(
                organisationId, propertyId, documentId, validated.originalFilename(), validated.extension());

        repository.insert(
                documentId,
                organisationId,
                propertyId,
                type.value(),
                validated.originalFilename(),
                validated.contentType(),
                validated.sizeBytes(),
                bucket,
                key,
                null);

        try (InputStream in = validated.openStream()) {
            storage.put(bucket, key, in, validated.sizeBytes(), validated.contentType());
        } catch (IOException | RuntimeException e) {
            throw storageUnavailable();
        }

        return new DocumentItem(
                documentId,
                propertyId,
                type.value(),
                validated.originalFilename(),
                validated.contentType(),
                validated.sizeBytes(),
                clock.instant());
    }

    @Transactional(readOnly = true)
    public SignedUrlResult signedUrl(UUID documentId) {
        DocumentRow row = repository.findActiveById(documentId).orElseThrow(DocumentService::documentNotFound);
        Duration ttl = properties.signedUrlTtl();
        Instant expiresAt = clock.instant().plus(ttl);
        try {
            String url = storage.presignGet(row.storageBucket(), row.storageKey(), ttl).toString();
            return new SignedUrlResult(url, expiresAt);
        } catch (RuntimeException e) {
            throw storageUnavailable();
        }
    }

    @Transactional
    public void softDelete(UUID documentId) {
        if (!repository.softDelete(documentId)) {
            throw documentNotFound();
        }
    }

    private static ApiException propertyNotFound() {
        return new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    private static ApiException documentNotFound() {
        return new ApiException("DOCUMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    private static ApiException storageUnavailable() {
        return new ApiException("DOCUMENT_STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
