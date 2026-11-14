package com.immodata.gb.infrastructure.persistence.admin;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.application.admin.AuditEventView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepository {

    private static final RowMapper<AuditEventView> VIEW_MAPPER = (rs, rowNum) -> new AuditEventView(
            rs.getObject("id", UUID.class),
            rs.getString("entity_type"),
            rs.getObject("entity_id", UUID.class),
            rs.getString("action"),
            rs.getString("old_value"),
            rs.getString("new_value"),
            rs.getString("summary"),
            toInstant(rs.getObject("created_at", OffsetDateTime.class)));

    private final JdbcClient jdbcClient;

    public AuditEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public void insert(
            String entityType, UUID entityId, String action, String oldJson, String newJson, String summary) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO audit_event (
                          entity_type, entity_id, action, old_value, new_value, summary
                        ) VALUES (
                          :entityType, :entityId, :action,
                          CAST(:oldJson AS jsonb), CAST(:newJson AS jsonb), :summary
                        )
                        """)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("action", action)
                .param("oldJson", oldJson)
                .param("newJson", newJson)
                .param("summary", summary)
                .update();
    }

    public PageResponse<AuditEventView> find(
            String entityType, String action, Instant from, Instant to, int page, int size) {
        String whereSql = whereClause(entityType, action, from, to);
        long total = bind(
                        jdbcClient.sql("SELECT count(*) FROM audit_event" + whereSql), entityType, action, from, to)
                .query(Long.class)
                .single();
        String listSql = "SELECT id, entity_type, entity_id, action, "
                + "old_value::text AS old_value, new_value::text AS new_value, summary, created_at "
                + "FROM audit_event"
                + whereSql
                + " ORDER BY created_at DESC, id LIMIT :size OFFSET :offset";
        List<AuditEventView> items = bind(jdbcClient.sql(listSql), entityType, action, from, to)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(VIEW_MAPPER)
                .list();
        return PageResponse.of(page, size, total, items);
    }

    private static String whereClause(String entityType, String action, Instant from, Instant to) {
        StringBuilder where = new StringBuilder();
        append(where, entityType != null, "entity_type = :entityType");
        append(where, action != null, "action = :action");
        append(where, from != null, "created_at >= :from");
        append(where, to != null, "created_at <= :to");
        return where.isEmpty() ? "" : " WHERE " + where;
    }

    private static void append(StringBuilder where, boolean include, String clause) {
        if (!include) {
            return;
        }
        if (!where.isEmpty()) {
            where.append(" AND ");
        }
        where.append(clause);
    }

    private static JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec spec, String entityType, String action, Instant from, Instant to) {
        if (entityType != null) {
            spec = spec.param("entityType", entityType);
        }
        if (action != null) {
            spec = spec.param("action", action);
        }
        if (from != null) {
            spec = spec.param("from", from.atOffset(ZoneOffset.UTC));
        }
        if (to != null) {
            spec = spec.param("to", to.atOffset(ZoneOffset.UTC));
        }
        return spec;
    }
}
