package com.immodata.gb.infrastructure.persistence.document;

import com.immodata.gb.application.document.DocumentItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    /** Ligne complète (usage interne : URL signée, suppression). Ne jamais exposer via l'API. */
    public record DocumentRow(
            UUID id,
            UUID organisationId,
            UUID propertyId,
            String documentType,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String storageBucket,
            String storageKey,
            Instant createdAt) {}

    private static final RowMapper<DocumentItem> ITEM_MAPPER = (rs, rowNum) -> new DocumentItem(
            rs.getObject("id", UUID.class),
            rs.getObject("property_id", UUID.class),
            rs.getString("document_type"),
            rs.getString("original_filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            toInstant(rs.getObject("created_at", OffsetDateTime.class)));

    private static final RowMapper<DocumentRow> ROW_MAPPER = (rs, rowNum) -> new DocumentRow(
            rs.getObject("id", UUID.class),
            rs.getObject("organisation_id", UUID.class),
            rs.getObject("property_id", UUID.class),
            rs.getString("document_type"),
            rs.getString("original_filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            rs.getString("storage_bucket"),
            rs.getString("storage_key"),
            toInstant(rs.getObject("created_at", OffsetDateTime.class)));

    private final JdbcClient jdbcClient;

    public DocumentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public boolean propertyExistsActiveWithGeometry(UUID propertyId) {
        return jdbcClient
                .sql(
                        """
                        SELECT EXISTS (
                          SELECT 1 FROM property p
                          JOIN property_geometry g ON g.property_id = p.id
                          WHERE p.id = :id AND p.deleted_at IS NULL
                        )
                        """)
                .param("id", propertyId)
                .query(Boolean.class)
                .single();
    }

    public Optional<UUID> findOrganisationIdForActiveProperty(UUID propertyId) {
        return jdbcClient
                .sql(
                        """
                        SELECT p.organisation_id
                        FROM property p
                        JOIN property_geometry g ON g.property_id = p.id
                        WHERE p.id = :id AND p.deleted_at IS NULL
                        """)
                .param("id", propertyId)
                .query(UUID.class)
                .optional();
    }

    public List<DocumentItem> listActiveByProperty(UUID propertyId) {
        return jdbcClient
                .sql(
                        """
                        SELECT d.id, d.property_id, d.document_type, d.original_filename, d.content_type, d.size_bytes, d.created_at
                        FROM document d
                        WHERE d.property_id = :propertyId AND d.deleted_at IS NULL
                        ORDER BY d.created_at DESC, d.id
                        """)
                .param("propertyId", propertyId)
                .query(ITEM_MAPPER)
                .list();
    }

    public void insert(
            UUID id,
            UUID organisationId,
            UUID propertyId,
            String documentType,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String storageBucket,
            String storageKey,
            UUID createdBy) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO document (
                          id, organisation_id, property_id, document_type, original_filename,
                          content_type, size_bytes, storage_bucket, storage_key, created_by, updated_by
                        ) VALUES (
                          :id, :organisationId, :propertyId, :documentType, :originalFilename,
                          :contentType, :sizeBytes, :storageBucket, :storageKey, :createdBy, :createdBy
                        )
                        """)
                .param("id", id)
                .param("organisationId", organisationId)
                .param("propertyId", propertyId)
                .param("documentType", documentType)
                .param("originalFilename", originalFilename)
                .param("contentType", contentType)
                .param("sizeBytes", sizeBytes)
                .param("storageBucket", storageBucket)
                .param("storageKey", storageKey)
                .param("createdBy", createdBy)
                .update();
    }

    public Optional<DocumentRow> findActiveById(UUID id) {
        return jdbcClient
                .sql(
                        """
                        SELECT d.id, d.organisation_id, d.property_id, d.document_type, d.original_filename,
                               d.content_type, d.size_bytes, d.storage_bucket, d.storage_key, d.created_at
                        FROM document d
                        WHERE d.id = :id AND d.deleted_at IS NULL
                        """)
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public boolean softDelete(UUID id) {
        return jdbcClient
                        .sql(
                                """
                                UPDATE document
                                SET deleted_at = now(), updated_at = now()
                                WHERE id = :id AND deleted_at IS NULL
                                """)
                        .param("id", id)
                        .update()
                == 1;
    }
}
