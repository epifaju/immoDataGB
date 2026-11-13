package com.immodata.gb.infrastructure.storage;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Clé d'objet : {@code {org}/{property}/{doc}/{safeStem}.{ext}}. */
@Component
public class StorageKeyFactory {

    static final int MAX_STEM_LENGTH = 100;
    private static final String FALLBACK_STEM = "document";

    /**
     * @param validatedExtension extension issue de la whitelist validée (jamais déduite du nom client)
     */
    public String build(
            UUID organisationId,
            UUID propertyId,
            UUID documentId,
            String originalFilename,
            String validatedExtension) {
        String extension = validatedExtension.toLowerCase(Locale.ROOT);
        return organisationId + "/" + propertyId + "/" + documentId + "/" + safeStem(originalFilename) + "." + extension;
    }

    private static String safeStem(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        String stem = dot >= 0 ? name.substring(0, dot) : name;
        String sanitized = stem.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.length() > MAX_STEM_LENGTH) {
            sanitized = sanitized.substring(0, MAX_STEM_LENGTH);
        }
        if (sanitized.isBlank() || sanitized.chars().allMatch(c -> c == '.')) {
            return FALLBACK_STEM;
        }
        return sanitized;
    }
}
