package com.immodata.gb.infrastructure.persistence.confidence;

import com.immodata.gb.domain.confidence.SourceFact;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConfidenceRepository {

    /** Bien actif avec géométrie, verrouillé pour la durée de la transaction. */
    public record LockedProperty(UUID id, UUID organisationId, String docStatus) {}

    private static final RowMapper<LockedProperty> LOCKED_MAPPER = (rs, rowNum) -> new LockedProperty(
            rs.getObject("id", UUID.class),
            rs.getObject("organisation_id", UUID.class),
            rs.getString("doc_status"));

    private final JdbcClient jdbcClient;

    public ConfidenceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<LockedProperty> lockActivePropertiesForOrg(UUID orgId) {
        return jdbcClient
                .sql(
                        """
                        SELECT p.id, p.organisation_id, p.doc_status
                        FROM property p
                        JOIN property_geometry g ON g.property_id = p.id
                        WHERE p.deleted_at IS NULL AND p.organisation_id = :orgId
                        ORDER BY p.id
                        FOR UPDATE OF p
                        """)
                .param("orgId", orgId)
                .query(LOCKED_MAPPER)
                .list();
    }

    public Optional<LockedProperty> lockActiveProperty(UUID id) {
        return jdbcClient
                .sql(
                        """
                        SELECT p.id, p.organisation_id, p.doc_status
                        FROM property p
                        JOIN property_geometry g ON g.property_id = p.id
                        WHERE p.deleted_at IS NULL AND p.id = :id
                        ORDER BY p.id
                        FOR UPDATE OF p
                        """)
                .param("id", id)
                .query(LOCKED_MAPPER)
                .optional();
    }

    public Map<UUID, List<SourceFact>> loadSourceFacts(Collection<UUID> propertyIds) {
        Map<UUID, List<SourceFact>> result = new HashMap<>();
        if (propertyIds.isEmpty()) {
            return result;
        }
        jdbcClient
                .sql(
                        """
                        SELECT s.property_id, s.source_type, s.weight, s.is_verified, s.observed_at
                        FROM source s
                        WHERE s.property_id IN (:ids)
                        ORDER BY s.property_id, s.created_at, s.id
                        """)
                .param("ids", propertyIds)
                .query(rs -> {
                    result.computeIfAbsent(rs.getObject("property_id", UUID.class), k -> new ArrayList<>())
                            .add(new SourceFact(
                                    rs.getString("source_type"),
                                    rs.getInt("weight"),
                                    rs.getBoolean("is_verified"),
                                    rs.getObject("observed_at", LocalDate.class)));
                });
        return result;
    }

    public void updateConfidence(UUID id, int score, String level, String summary) {
        jdbcClient
                .sql(
                        """
                        UPDATE property
                        SET confidence_score = :score,
                            confidence_level = :level,
                            confidence_summary = :summary,
                            updated_at = now()
                        WHERE id = :id AND deleted_at IS NULL
                        """)
                .param("score", score)
                .param("level", level)
                .param("summary", summary)
                .param("id", id)
                .update();
    }

    public boolean organisationExistsActive(UUID id) {
        return jdbcClient
                .sql("SELECT EXISTS (SELECT 1 FROM organisation WHERE id = :id AND deleted_at IS NULL)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }
}
