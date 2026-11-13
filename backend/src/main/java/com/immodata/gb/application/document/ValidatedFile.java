package com.immodata.gb.application.document;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fichier ayant passé la validation (taille, extension whitelist, MIME Tika).
 * Le contenu n'est pas copié en mémoire : {@link #openStream()} rouvre le flux source.
 */
public record ValidatedFile(
        String originalFilename, String extension, String contentType, long sizeBytes, MultipartFile source) {

    public InputStream openStream() throws IOException {
        return source.getInputStream();
    }
}
