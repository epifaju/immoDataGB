package com.immodata.gb.infrastructure.persistence.admin;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.application.admin.PendingProperty;
import com.immodata.gb.application.admin.ValidationQueueItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminValidationRepository {

    private static final RowMapper<ValidationQueueItem> QUEUE_MAPPER = (rs, rowNum) -> new ValidationQueueItem(
            rs.getObject("id", UUID.class),
            rs.getString("reference"),
            rs.getString("title"),
            rs.getString("property_type"),
            toInstant(rs.getObject("created_at", OffsetDateTime.class)),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"));

    private static final RowMapper<PendingProperty> PENDING_MAPPER = (rs, rowNum) -> new PendingProperty(
            rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("validation_status"));

    private final JdbcClient jdbcClient;

    public AdminValidationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public PageResponse<ValidationQueueItem> findPendingQueue(int page, int size) {
        long total = jdbcClient
                .sql(
                        """
                        SELECT count(*)
                        FROM property p
                        JOIN property_geometry g ON g.property_id = p.id
                        WHERE p.deleted_at IS NULL
                          AND p.validation_status = 'pending_validation'
                        """)
                .query(Long.class)
                .single();
        List<ValidationQueueItem> items = jdbcClient
                .sql(
                        """
                        SELECT p.id, p.reference, p.title, p.property_type, p.created_at,
                               ST_X(g.geom) AS longitude, ST_Y(g.geom) AS latitude
                        FROM property p
                        JOIN property_geometry g ON g.property_id = p.id
                        WHERE p.deleted_at IS NULL
                          AND p.validation_status = 'pending_validation'
                        ORDER BY p.created_at DESC, p.id
                        LIMIT :size OFFSET :offset
                        """)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(QUEUE_MAPPER)
                .list();
        return PageResponse.of(page, size, total, items);
    }

    public Optional<PendingProperty> findPendingById(UUID id) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, reference, validation_status
                        FROM property
                        WHERE id = :id
                          AND deleted_at IS NULL
                          AND validation_status = 'pending_validation'
                        """)
                .param("id", id)
                .query(PENDING_MAPPER)
                .optional();
    }

    public Optional<ValidationQueueItem> findPendingItemById(UUID id) {
        return jdbcClient
                .sql(
                        """
                        SELECT p.id, p.reference, p.title, p.property_type, p.created_at,
                               ST_X(g.geom) AS longitude, ST_Y(g.geom) AS latitude
                        FROM property p
                        JOIN property_geometry g ON g.property_id = p.id
                        WHERE p.id = :id
                          AND p.deleted_at IS NULL
                          AND p.validation_status = 'pending_validation'
                        """)
                .param("id", id)
                .query(QUEUE_MAPPER)
                .optional();
    }

    public void markValidated(UUID id) {
        jdbcClient
                .sql(
                        """
                        UPDATE property
                        SET validation_status = 'validated',
                            updated_at = now()
                        WHERE id = :id
                          AND deleted_at IS NULL
                          AND validation_status = 'pending_validation'
                        """)
                .param("id", id)
                .update();
    }

    public void softDelete(UUID id) {
        jdbcClient
                .sql(
                        """
                        UPDATE property
                        SET deleted_at = now(),
                            updated_at = now()
                        WHERE id = :id
                          AND deleted_at IS NULL
                          AND validation_status = 'pending_validation'
                        """)
                .param("id", id)
                .update();
    }
}
