package com.immodata.gb.application.document;

import com.immodata.gb.api.error.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validation d'un upload : présence, taille, extension (whitelist), MIME détecté par Apache Tika.
 * Ordre : {@code DOCUMENT_FILE_MISSING} → {@code DOCUMENT_FILE_TOO_LARGE} → {@code DOCUMENT_FILE_TYPE_REJECTED}.
 */
@Component
public class DocumentFileValidator {

    public static final long MAX_SIZE_BYTES = 52_428_800L;

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String DOCX_MAIN_PART = DOCX_MIME + ".main+xml";
    private static final String XLSX_MAIN_PART = XLSX_MIME + ".main+xml";
    private static final Set<String> ZIP_CONTAINER_MIMES = Set.of("application/zip", "application/x-tika-ooxml");
    private static final int MAX_CONTENT_TYPES_BYTES = 1_048_576;
    private static final int MAX_ZIP_ENTRIES_INSPECTED = 1_000;

    private static final Map<String, Set<String>> ALLOWED_MIMES = Map.of(
            "pdf", Set.of("application/pdf"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "webp", Set.of("image/webp"),
            "heic", Set.of("image/heic", "image/heif"),
            "docx", Set.of(DOCX_MIME),
            "xlsx", Set.of(XLSX_MIME));

    private final Tika tika = new Tika();

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw reject("DOCUMENT_FILE_MISSING");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw reject("DOCUMENT_FILE_TOO_LARGE");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = extensionOf(originalFilename);
        Set<String> allowedMimes = extension == null ? null : ALLOWED_MIMES.get(extension);
        if (allowedMimes == null) {
            throw reject("DOCUMENT_FILE_TYPE_REJECTED");
        }
        String detected = detectMime(file);
        if (!allowedMimes.contains(detected)) {
            throw reject("DOCUMENT_FILE_TYPE_REJECTED");
        }
        return new ValidatedFile(originalFilename, extension, detected, file.getSize(), file);
    }

    /** Détection sur le contenu uniquement (le nom client n'est jamais transmis à Tika). */
    private String detectMime(MultipartFile file) {
        try {
            String detected;
            try (InputStream in = file.getInputStream()) {
                detected = tika.detect(in);
            }
            if (ZIP_CONTAINER_MIMES.contains(detected)) {
                try (InputStream in = file.getInputStream()) {
                    return refineOoxml(in, detected);
                }
            }
            return detected;
        } catch (IOException e) {
            throw reject("DOCUMENT_FILE_TYPE_REJECTED");
        }
    }

    /**
     * {@code tika-core} seul ne distingue pas docx / xlsx d'un zip générique : on lit uniquement
     * {@code [Content_Types].xml} (borné) et on y cherche le type de la partie principale.
     */
    private static String refineOoxml(InputStream in, String fallback) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < MAX_ZIP_ENTRIES_INSPECTED) {
                if (!"[Content_Types].xml".equals(entry.getName())) {
                    continue;
                }
                byte[] head = zip.readNBytes(MAX_CONTENT_TYPES_BYTES);
                String xml = new String(head, StandardCharsets.UTF_8);
                if (xml.contains(DOCX_MAIN_PART)) {
                    return DOCX_MIME;
                }
                if (xml.contains(XLSX_MAIN_PART)) {
                    return XLSX_MIME;
                }
                return fallback;
            }
        }
        return fallback;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String trimmed = filename.trim();
        int dot = trimmed.lastIndexOf('.');
        if (dot < 0 || dot == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static ApiException reject(String code) {
        return new ApiException(code, HttpStatus.BAD_REQUEST);
    }
}
