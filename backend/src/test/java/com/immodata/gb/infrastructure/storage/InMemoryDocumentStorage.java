package com.immodata.gb.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stub de stockage pour tests d'intégration : clé {@code bucket/key} vers octets. */
public class InMemoryDocumentStorage implements DocumentStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final Map<String, String> contentTypes = new ConcurrentHashMap<>();

    @Override
    public void put(String bucket, String key, InputStream data, long sizeBytes, String contentType) {
        try {
            objects.put(id(bucket, key), data.readAllBytes());
            contentTypes.put(id(bucket, key), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public URI presignGet(String bucket, String key, Duration ttl) {
        return URI.create("http://localhost/in-memory/" + id(bucket, key) + "?expires=" + ttl.toSeconds());
    }

    public boolean contains(String bucket, String key) {
        return objects.containsKey(id(bucket, key));
    }

    public byte[] get(String bucket, String key) {
        return objects.get(id(bucket, key));
    }

    public String contentType(String bucket, String key) {
        return contentTypes.get(id(bucket, key));
    }

    public int size() {
        return objects.size();
    }

    public void clear() {
        objects.clear();
        contentTypes.clear();
    }

    private static String id(String bucket, String key) {
        return bucket + "/" + key;
    }
}