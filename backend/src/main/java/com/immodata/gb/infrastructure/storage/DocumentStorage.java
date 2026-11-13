package com.immodata.gb.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/** Port de stockage objet des documents (S3/MinIO). Les échecs SDK remontent à l'appelant. */
public interface DocumentStorage {

    void put(String bucket, String key, InputStream data, long sizeBytes, String contentType);

    /** URL GET signée, valide pendant {@code ttl}. */
    URI presignGet(String bucket, String key, Duration ttl);
}