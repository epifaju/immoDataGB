package com.immodata.gb.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Adaptateur S3/MinIO. Instancié par {@link S3ClientConfig} uniquement si un endpoint est configuré. */
public class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3DocumentStorage(S3Client s3Client, S3Presigner presigner) {
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    @Override
    public void put(String bucket, String key, InputStream data, long sizeBytes, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(data, sizeBytes));
    }

    @Override
    public URI presignGet(String bucket, String key, Duration ttl) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        try {
            return presigner.presignGetObject(request).url().toURI();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Invalid presigned URL", e);
        }
    }
}