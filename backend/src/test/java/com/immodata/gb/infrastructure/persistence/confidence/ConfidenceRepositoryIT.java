package com.immodata.gb.infrastructure.persistence.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.immodata.gb.application.confidence.ConfidenceRuleInvalidException;
import com.immodata.gb.domain.confidence.ConfidenceRule;
import com.immodata.gb.domain.confidence.SourceFact;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class ConfidenceRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("immodata")
            .withUsername("immodata")
            .withPassword("immodata")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ConfidenceRepository repository;

    @Autowired
    private ConfidenceRuleRepository ruleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private UUID newOrganisation() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO organisation (id, name) VALUES (?, ?)", id, "org-" + id);
        return id;
    }

    private void insertRule(UUID orgId, String name, String type, String json, boolean active) {
        jdbc.update(
                "INSERT INTO confidence_rule (organisation_id, rule_name, rule_type, condition_json, score_delta,"
                        + " is_active, priority) VALUES (?, ?, ?, ?::jsonb, 5, ?, 1)",
                orgId, name, type, json, active);
    }

    private UUID insertProperty(UUID orgId, String reference, boolean withGeometry, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO property (id, organisation_id, reference, title, property_type, confidence_score,"
                        + " confidence_level, confidence_summary, deleted_at)"
                        + " VALUES (?, ?, ?, 't', 'house', 0, 'low', 'x', CASE WHEN ? THEN now() END)",
                id, orgId, reference, deleted);
        if (withGeometry) {
            jdbc.update(
                    "INSERT INTO property_geometry (property_id, geom, geom_type)"
                            + " VALUES (?, ST_SetSRID(ST_MakePoint(-15.6, 11.86), 4326), 'point')",
                    id);
        }
        return id;
    }

    @Test
    void seededRulesParseStrictly() {
        UUID orgId = jdbc.queryForObject(
                "SELECT organisation_id FROM confidence_rule WHERE is_active GROUP BY organisation_id"
                        + " ORDER BY count(*) DESC LIMIT 1",
                UUID.class);
        List<ConfidenceRule> rules = ruleRepository.listActiveParsed(orgId);
        assertThat(rules).isNotEmpty();
        assertThat(rules).extracting(ConfidenceRule::priority).isSorted();
    }

    @Test
    void listActiveParsedSkipsInactiveAndFailsOnInvalidActiveRule() {
        UUID orgId = newOrganisation();
        insertRule(orgId, "ok", "age_penalty", "{\"olderThanMonths\":12}", true);
        insertRule(orgId, "inactive_bad", "age_penalty", "{\"olderThanMonths\":0}", false);
        assertThat(ruleRepository.listActiveParsed(orgId)).extracting(ConfidenceRule::name).containsExactly("ok");

        insertRule(orgId, "active_bad", "source_count", "{\"minUnits\":\"x\",\"maxUnits\":null}", true);
        assertThatThrownBy(() -> ruleRepository.listActiveParsed(orgId))
                .isInstanceOfSatisfying(ConfidenceRuleInvalidException.class, e ->
                        assertThat(e.getRuleName()).isEqualTo("active_bad"));
    }

    @Test
    void locksOnlyActivePropertiesWithGeometryAndLoadsSources() {
        UUID orgId = newOrganisation();
        UUID p1 = insertProperty(orgId, "REF-" + UUID.randomUUID(), true, false);
        UUID p2 = insertProperty(orgId, "REF-" + UUID.randomUUID(), true, false);
        UUID noGeom = insertProperty(orgId, "REF-" + UUID.randomUUID(), false, false);
        UUID deleted = insertProperty(orgId, "REF-" + UUID.randomUUID(), true, true);
        jdbc.update("INSERT INTO source (property_id, source_type, observed_at, is_verified, weight)"
                + " VALUES (?, 'partner', DATE '2026-01-02', true, 3)", p1);
        jdbc.update("INSERT INTO source (property_id, source_type) VALUES (?, 'open_layer')", p1);

        tx.executeWithoutResult(status -> {
            List<ConfidenceRepository.LockedProperty> locked = repository.lockActivePropertiesForOrg(orgId);
            assertThat(locked).extracting(ConfidenceRepository.LockedProperty::id)
                    .containsExactlyInAnyOrder(p1, p2)
                    .doesNotContain(noGeom, deleted)
                    .isSortedAccordingTo(java.util.Comparator.comparing(UUID::toString));
            assertThat(locked).allSatisfy(l -> {
                assertThat(l.organisationId()).isEqualTo(orgId);
                assertThat(l.docStatus()).isEqualTo("none");
            });

            assertThat(repository.lockActiveProperty(p1)).isPresent();
            assertThat(repository.lockActiveProperty(noGeom)).isEmpty();
            assertThat(repository.lockActiveProperty(deleted)).isEmpty();
            assertThat(repository.lockActiveProperty(UUID.randomUUID())).isEmpty();
        });

        Map<UUID, List<SourceFact>> facts = repository.loadSourceFacts(List.of(p1, p2));
        assertThat(facts).containsOnlyKeys(p1);
        assertThat(facts.get(p1)).hasSize(2);
        assertThat(facts.get(p1)).anySatisfy(f -> {
            assertThat(f.sourceType()).isEqualTo("partner");
            assertThat(f.weight()).isEqualTo(3);
            assertThat(f.verified()).isTrue();
            assertThat(f.observedAt()).hasToString("2026-01-02");
        });
        assertThat(facts.get(p1)).anySatisfy(f -> {
            assertThat(f.weight()).isEqualTo(1);
            assertThat(f.verified()).isFalse();
            assertThat(f.observedAt()).isNull();
        });
        assertThat(repository.loadSourceFacts(List.of())).isEmpty();
    }

    @Test
    void updateConfidenceWritesColumnsAndSkipsDeleted() {
        UUID orgId = newOrganisation();
        UUID active = insertProperty(orgId, "REF-" + UUID.randomUUID(), true, false);
        UUID deleted = insertProperty(orgId, "REF-" + UUID.randomUUID(), true, true);

        repository.updateConfidence(active, 70, "high", "resumo");
        repository.updateConfidence(deleted, 70, "high", "resumo");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT confidence_score, confidence_level, confidence_summary FROM property WHERE id = ?", active);
        assertThat(((Number) row.get("confidence_score")).intValue()).isEqualTo(70);
        assertThat(row.get("confidence_level")).isEqualTo("high");
        assertThat(row.get("confidence_summary")).isEqualTo("resumo");
        assertThat(jdbc.queryForObject("SELECT confidence_level FROM property WHERE id = ?", String.class, deleted))
                .isEqualTo("low");
    }

    @Test
    void organisationExistsActiveIgnoresDeletedAndUnknown() {
        UUID orgId = newOrganisation();
        UUID deletedOrg = newOrganisation();
        jdbc.update("UPDATE organisation SET deleted_at = now() WHERE id = ?", deletedOrg);

        assertThat(repository.organisationExistsActive(orgId)).isTrue();
        assertThat(repository.organisationExistsActive(deletedOrg)).isFalse();
        assertThat(repository.organisationExistsActive(UUID.randomUUID())).isFalse();
    }
}
