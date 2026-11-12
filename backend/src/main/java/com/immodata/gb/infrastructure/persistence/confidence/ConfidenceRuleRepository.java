package com.immodata.gb.infrastructure.persistence.confidence;

import com.immodata.gb.domain.confidence.ConfidenceRule;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConfidenceRuleRepository {

    private final JdbcClient jdbcClient;
    private final ConfidenceConditionReader reader = new ConfidenceConditionReader();

    public ConfidenceRuleRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Règles actives de l'organisation, ordre (priority, id), toutes parsées et validées. La première règle
     * invalide lève {@link com.immodata.gb.application.confidence.ConfidenceRuleInvalidException}.
     */
    public List<ConfidenceRule> listActiveParsed(UUID organisationId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, rule_name, rule_type, condition_json::text AS condition_json,
                               score_delta, priority
                        FROM confidence_rule
                        WHERE organisation_id = :orgId AND is_active
                        ORDER BY priority, id
                        """)
                .param("orgId", organisationId)
                .query((rs, rowNum) -> reader.read(
                        rs.getObject("id", UUID.class),
                        rs.getString("rule_name"),
                        rs.getString("rule_type"),
                        rs.getString("condition_json"),
                        rs.getInt("score_delta"),
                        rs.getInt("priority")))
                .list();
    }
}
