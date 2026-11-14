package com.immodata.gb.infrastructure.persistence.field;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FieldObservationRepository {

    public static final int INITIAL_CONFIDENCE_SCORE = 12;
    public static final String INITIAL_CONFIDENCE_LEVEL = "low";
    public static final String INITIAL_CONFIDENCE_SUMMARY = "Fonte declarativa única; verificação pendente.";
    public static final String INITIAL_DOC_STATUS = "none";
    public static final String VALIDATION_STATUS_PENDING = "pending_validation";
    public static final String SOURCE_TYPE_FIELD_OBSERVATION = "field_observation";

    private final JdbcClient jdbcClient;

    public FieldObservationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Insère de façon atomique property ({@code pending_validation}, confiance 12/low, doc_status none),
     * property_geometry (point GPS, SRID 4326) et source ({@code field_observation}).
     *
     * @param sourceLabel libellé de source (≤ 200) ou {@code null}
     * @return identifiant du bien créé
     */
    @Transactional
    public UUID insertPending(
            UUID organisationId,
            String reference,
            String title,
            String propertyType,
            double latitude,
            double longitude,
            String sourceLabel) {
        UUID propertyId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO property (
                          id, organisation_id, reference, title, property_type,
                          confidence_score, confidence_level, confidence_summary,
                          doc_status, validation_status
                        ) VALUES (
                          :id, :orgId, :reference, :title, :propertyType,
                          :score, :level, :summary,
                          :docStatus, :validationStatus
                        )
                        """)
                .param("id", propertyId)
                .param("orgId", organisationId)
                .param("reference", reference)
                .param("title", title)
                .param("propertyType", propertyType)
                .param("score", INITIAL_CONFIDENCE_SCORE)
                .param("level", INITIAL_CONFIDENCE_LEVEL)
                .param("summary", INITIAL_CONFIDENCE_SUMMARY)
                .param("docStatus", INITIAL_DOC_STATUS)
                .param("validationStatus", VALIDATION_STATUS_PENDING)
                .update();

        jdbcClient
                .sql(
                        """
                        INSERT INTO property_geometry (property_id, geom, geom_type, precision_level)
                        VALUES (:propertyId, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 'point', 'gps')
                        """)
                .param("propertyId", propertyId)
                .param("lng", longitude)
                .param("lat", latitude)
                .update();

        jdbcClient
                .sql(
                        """
                        INSERT INTO source (property_id, source_type, label)
                        VALUES (:propertyId, :sourceType, :label)
                        """)
                .param("propertyId", propertyId)
                .param("sourceType", SOURCE_TYPE_FIELD_OBSERVATION)
                .param("label", sourceLabel)
                .update();

        return propertyId;
    }
}
