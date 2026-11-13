package com.immodata.gb.application.document;

import java.time.Instant;
import java.util.UUID;

/** Document exposé par l'API (sans clé de stockage, bucket ni organisation). */
public record DocumentItem(
        UUID id,
        UUID propertyId,
        String documentType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant createdAt) {}
