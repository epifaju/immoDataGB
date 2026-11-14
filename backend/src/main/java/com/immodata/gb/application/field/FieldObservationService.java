package com.immodata.gb.application.field;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.config.FieldProperties;
import com.immodata.gb.infrastructure.persistence.field.FieldObservationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Création d'une observation terrain : toujours {@code pending_validation}, confiance initiale 12/low.
 * Garde : drapeau {@code app.field.mutations-enabled} puis {@code app.field.organisation-id}.
 */
@Service
public class FieldObservationService {

    private static final Logger log = LoggerFactory.getLogger(FieldObservationService.class);

    static final int MAX_LABEL_LENGTH = 200;
    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final FieldObservationRepository repository;
    private final FieldProperties properties;
    private final Clock clock;

    public FieldObservationService(FieldObservationRepository repository, FieldProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public FieldObservationCreated create(FieldObservationRequest req) {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        UUID organisationId = properties.organisationId();
        if (organisationId == null) {
            log.error("app.field.organisation-id is not configured while field mutations are enabled");
            throw new ApiException("FIELD_ORGANISATION_MISSING", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String reference = generateReference();
        String title = resolveTitle(req.title(), reference);
        String label = buildLabel(req.occupationStatus(), req.notes());

        UUID id = repository.insertPending(
                organisationId,
                reference,
                title,
                req.propertyType(),
                req.latitude(),
                req.longitude(),
                label);

        return new FieldObservationCreated(
                id,
                reference,
                title,
                req.propertyType(),
                FieldObservationRepository.VALIDATION_STATUS_PENDING,
                req.latitude(),
                req.longitude(),
                FieldObservationRepository.INITIAL_CONFIDENCE_SCORE,
                FieldObservationRepository.INITIAL_CONFIDENCE_LEVEL);
    }

    private String generateReference() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "FLD-" + REFERENCE_DATE.format(today) + "-" + hex;
    }

    private static String resolveTitle(String title, String reference) {
        if (title == null || title.isBlank()) {
            return "Observação de campo (" + reference + ")";
        }
        return title.trim();
    }

    static String buildLabel(String occupationStatus, String notes) {
        String trimmedNotes = notes == null ? "" : notes.trim();
        String occupation = occupationStatus == null ? "" : occupationStatus.trim();
        String raw;
        if (occupation.isEmpty()) {
            raw = trimmedNotes;
        } else if (trimmedNotes.isEmpty()) {
            raw = "[" + occupation + "]";
        } else {
            raw = "[" + occupation + "] " + trimmedNotes;
        }
        if (raw.length() > MAX_LABEL_LENGTH) {
            raw = raw.substring(0, MAX_LABEL_LENGTH);
        }
        raw = raw.trim();
        return raw.isEmpty() ? null : raw;
    }
}
