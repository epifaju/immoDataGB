package com.immodata.gb.api.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.immodata.gb.application.confidence.ConfidenceEngineService;
import com.immodata.gb.application.confidence.ConfidenceResult;
import com.immodata.gb.domain.property.ConfidenceLevel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloc 3 : moteur de confiance contre PostGIS. Profil {@code dev} par défaut (drapeau à {@code true}, seed chargé),
 * horloge fixée au 2026-10-31. Chaque test est {@code @Transactional} (rollback) : MockMvc s'exécute dans le même
 * thread, donc le service participe à la transaction du test et le seed d'origine (Blocs 1 et 2) reste intact.
 * Les violations de contraintes tournent dans une transaction {@code REQUIRES_NEW} pour ne pas invalider celle du test.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfidenceEngineIT {

    private static final String ORG = "00000000-0000-0000-0000-00000000000a";
    private static final UUID ORG_ID = UUID.fromString(ORG);
    private static final Instant PILOT_NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final Instant LATER_NOW = Instant.parse("2028-12-01T00:00:00Z");

    /** n, score brut, score, niveau — tableau de référence de la spec (horloge au 2026-10-31). */
    private static final Object[][] EXPECTED = {
        {1, 35, 35, "medium"}, {2, 45, 45, "medium"}, {3, 30, 30, "low"}, {4, 80, 80, "high"},
        {5, 5, 5, "low"}, {6, 20, 20, "low"}, {7, 45, 45, "medium"}, {8, 45, 45, "medium"},
        {9, 65, 65, "medium"}, {10, 25, 25, "low"}, {11, 55, 55, "medium"}, {12, 50, 50, "medium"},
        {13, 45, 45, "medium"}, {14, 80, 80, "high"}, {15, -10, 0, "low"}, {16, 35, 35, "medium"},
        {17, 45, 45, "medium"}, {18, 30, 30, "low"}, {19, 80, 80, "high"}, {20, 5, 5, "low"},
        {21, 40, 40, "medium"}, {22, 65, 65, "medium"}, {23, 65, 65, "medium"}, {24, 65, 65, "medium"},
    };

    private static final Map<Integer, String> SUMMARIES = Map.of(
            1, "Pontuação 35/100 (média). Critérios: 5 unidades de fonte (+15); tipo de fonte: observação de campo (+20).",
            2, "Pontuação 45/100 (média). Critérios: 6 unidades de fonte (+15); tipo de fonte: observação de campo (+20);"
                    + " documentação declarada (+10).",
            3, "Pontuação 30/100 (baixa). Critérios: 1 unidade de fonte (+20); documentação parcial (+10).",
            4, "Pontuação 80/100 (alta). Critérios: 5 unidades de fonte (+15); tipo de fonte: observação de campo (+20);"
                    + " 1 fonte verificada (+20); documentação verificada (+25).",
            5, "Pontuação 5/100 (baixa). Critérios: 6 unidades de fonte (+15); tipo de fonte: observação de campo (+20);"
                    + " documentação em litígio (-30).",
            9, "Pontuação 65/100 (média). Critérios: 1 unidade de fonte (+20); 1 fonte verificada (+20);"
                    + " documentação verificada (+25).",
            15, "Pontuação 0/100 (baixa). Critérios: 1 unidade de fonte (+20); documentação em litígio (-30).");

    private static final String NO_EVIDENCE = "Sem evidências suficientes para pontuar.";

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

    /** Horloge modifiable : le service est câblé sur ce bean unique (primaire). */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(PILOT_NOW);

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConfidenceEngineService service;

    @Autowired
    private MutableClock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetClock() {
        clock.set(PILOT_NOW);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static UUID propertyId(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0001-%012d", n));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        // application/json sans charset : MockMvc décoderait en ISO-8859-1, JSON est UTF-8 (RFC 8259).
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode recalcProperty(UUID id, int expectedStatus) throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/properties/{id}/confidence/recalculate", id));
        actions.andExpect(status().is(expectedStatus));
        return json(actions);
    }

    private JsonNode recalcOrg(UUID orgId, int expectedStatus) throws Exception {
        ResultActions actions = mockMvc.perform(post("/api/v1/organisations/{id}/confidence/recalculate", orgId));
        actions.andExpect(status().is(expectedStatus));
        return json(actions);
    }

    private Map<Integer, Map<String, Object>> pilotRows() {
        Map<Integer, Map<String, Object>> rows = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id, confidence_score, confidence_level, confidence_summary FROM property"
                        + " WHERE organisation_id = ?::uuid AND deleted_at IS NULL",
                ORG)) {
            String id = row.get("id").toString();
            rows.put(Integer.parseInt(id.substring(id.length() - 2)), row);
        }
        return rows;
    }

    private Map<String, Integer> levelDistribution() {
        Map<String, Integer> distribution = new TreeMap<>();
        jdbc.query(
                "SELECT confidence_level, count(*) AS n FROM property"
                        + " WHERE organisation_id = ?::uuid AND deleted_at IS NULL GROUP BY confidence_level",
                rs -> {
                    distribution.put(rs.getString("confidence_level"), rs.getInt("n"));
                },
                ORG);
        return distribution;
    }

    private void assertViolation(String sql, Object... args) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> template.executeWithoutResult(status -> jdbc.update(sql, args)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertOrganisation() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO organisation (id, name) VALUES (?, ?)", id, "org-" + id);
        return id;
    }

    private UUID insertProperty(UUID orgId, boolean withGeometry, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO property (id, organisation_id, reference, title, property_type, confidence_score,"
                        + " confidence_level, confidence_summary, deleted_at)"
                        + " VALUES (?, ?, ?, 't', 'house', 50, 'medium', 'x', CASE WHEN ? THEN now() END)",
                id, orgId, "IT-" + id, deleted);
        if (withGeometry) {
            jdbc.update(
                    "INSERT INTO property_geometry (property_id, geom, geom_type)"
                            + " VALUES (?, ST_SetSRID(ST_MakePoint(-15.6, 11.86), 4326), 'point')",
                    id);
        }
        return id;
    }

    // ---- schéma V8 + seed ------------------------------------------------------------------------------------

    @Test
    void flywayAppliesV8AndSeedsNineRulesInReservedUuidRange() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);

        List<Map<String, Object>> rules = jdbc.queryForList(
                "SELECT id, organisation_id, rule_name, score_delta, is_active, priority FROM confidence_rule"
                        + " ORDER BY id");
        assertThat(rules).hasSize(9);
        List<String> expectedNames = List.of(
                "source_units_1", "source_units_2_plus", "field_observation_bonus", "verified_source_bonus",
                "doc_declared_bonus", "doc_partial_bonus", "doc_verified_bonus", "doc_litigious_penalty",
                "stale_sources_penalty");
        List<Integer> expectedDeltas = List.of(20, 15, 20, 20, 10, 10, 25, -30, -5);
        for (int i = 0; i < 9; i++) {
            Map<String, Object> rule = rules.get(i);
            assertThat(rule.get("id").toString()).isEqualTo(String.format("00000000-0000-0000-0004-%012d", i + 1));
            assertThat(rule.get("organisation_id").toString()).isEqualTo(ORG);
            assertThat(rule.get("rule_name")).isEqualTo(expectedNames.get(i));
            assertThat(((Number) rule.get("score_delta")).intValue()).isEqualTo(expectedDeltas.get(i));
            assertThat(rule.get("is_active")).isEqualTo(true);
        }
    }

    @Test
    void sourceWeightsFollowSourceType() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source", Integer.class)).isEqualTo(48);
        Map<String, Integer> weights = new TreeMap<>();
        jdbc.query(
                "SELECT source_type, min(weight) AS lo, max(weight) AS hi FROM source GROUP BY source_type",
                rs -> {
                    assertThat(rs.getInt("lo")).isEqualTo(rs.getInt("hi"));
                    weights.put(rs.getString("source_type"), rs.getInt("lo"));
                });
        assertThat(weights)
                .containsEntry("institutional", 3)
                .containsEntry("field_observation", 2)
                .containsEntry("agency_listing", 1)
                .hasSize(3);
    }

    // ---- contraintes -----------------------------------------------------------------------------------------

    @Test
    void sourceWeightOutOfRangeIsRejected() {
        String sql = "INSERT INTO source (property_id, source_type, weight) VALUES (?, 'agency_listing', ?)";
        assertViolation(sql, propertyId(1), 0);
        assertViolation(sql, propertyId(1), 101);
    }

    @Test
    void confidenceRuleConstraintsAreEnforced() {
        String insert = "INSERT INTO confidence_rule (organisation_id, rule_name, rule_type, condition_json,"
                + " score_delta, is_active, priority) VALUES (?, ?, ?, ?::jsonb, ?, ?, 1)";
        // type inconnu
        assertViolation(insert, ORG_ID, "it_unknown_type", "bogus", "{}", 5, true);
        // nom en double dans la même organisation
        assertViolation(insert, ORG_ID, "source_units_1", "source_count", "{\"minUnits\":1,\"maxUnits\":1}", 5, true);
        // clé requise absente
        assertViolation(insert, ORG_ID, "it_missing_key", "source_count", "{\"minUnits\":1}", 5, true);
        assertViolation(insert, ORG_ID, "it_missing_key2", "doc_status_bonus", "{}", 5, true);
        // condition_json non objet
        assertViolation(insert, ORG_ID, "it_not_object", "source_count", "[1,2]", 5, true);
        assertViolation(insert, ORG_ID, "it_scalar", "doc_status_bonus", "\"declared\"", 5, true);
        // inconsistency_penalty réservé : actif refusé
        assertViolation(insert, ORG_ID, "it_inconsistency", "inconsistency_penalty", "{}", -5, true);
        // score_delta hors bornes
        assertViolation(insert, ORG_ID, "it_delta_hi", "doc_status_bonus", "{\"docStatus\":\"none\"}", 101, true);
        assertViolation(insert, ORG_ID, "it_delta_lo", "doc_status_bonus", "{\"docStatus\":\"none\"}", -101, true);
    }

    @Test
    void inactiveInconsistencyPenaltyAndBoundaryDeltasAreAccepted() {
        String insert = "INSERT INTO confidence_rule (organisation_id, rule_name, rule_type, condition_json,"
                + " score_delta, is_active, priority) VALUES (?, ?, ?, ?::jsonb, ?, ?, 1)";
        assertThat(jdbc.update(insert, ORG_ID, "it_inconsistency_off", "inconsistency_penalty", "{}", -5, false))
                .isEqualTo(1);
        assertThat(jdbc.update(insert, ORG_ID, "it_delta_100", "doc_status_bonus", "{\"docStatus\":\"none\"}", 100, false))
                .isEqualTo(1);
        assertThat(jdbc.update(insert, ORG_ID, "it_delta_m100", "doc_status_bonus", "{\"docStatus\":\"none\"}", -100, false))
                .isEqualTo(1);
    }

    @Test
    void everyScoreFromZeroToHundredIsAcceptedByPropertyCheck() {
        UUID id = propertyId(1);
        for (int score = 0; score <= 100; score++) {
            String level = ConfidenceLevel.fromScore(score).getSqlValue();
            assertThat(jdbc.update(
                            "UPDATE property SET confidence_score = ?, confidence_level = ? WHERE id = ?",
                            score, level, id))
                    .isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT confidence_score FROM property WHERE id = ?", Integer.class, id))
                .isEqualTo(100);
    }

    @Test
    void inconsistentScoreAndLevelIsRejectedByPropertyCheck() {
        String sql = "UPDATE property SET confidence_score = ?, confidence_level = ? WHERE id = ?";
        assertViolation(sql, 50, "low", propertyId(1));
        assertViolation(sql, 101, "high", propertyId(1));
        assertViolation(sql, -1, "low", propertyId(1));
    }

    // ---- recalculs de référence ------------------------------------------------------------------------------

    @Test
    void sevenReferenceRecalculationsMatchExactSummaries() throws Exception {
        for (Map.Entry<Integer, String> entry : SUMMARIES.entrySet()) {
            int n = entry.getKey();
            Object[] expected = EXPECTED[n - 1];
            JsonNode data = recalcProperty(propertyId(n), 200).get("data");

            assertThat(data.get("propertyId").asText()).isEqualTo(propertyId(n).toString());
            assertThat(data.get("rawScore").asInt()).as("raw " + n).isEqualTo((Integer) expected[1]);
            assertThat(data.get("confidenceScore").asInt()).as("score " + n).isEqualTo((Integer) expected[2]);
            assertThat(data.get("confidenceLevel").asText()).as("level " + n).isEqualTo(expected[3]);
            assertThat(data.get("confidenceSummary").asText()).as("summary " + n).isEqualTo(entry.getValue());

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT confidence_score, confidence_level, confidence_summary FROM property WHERE id = ?",
                    propertyId(n));
            assertThat(((Number) row.get("confidence_score")).intValue()).isEqualTo((Integer) expected[2]);
            assertThat(row.get("confidence_level")).isEqualTo(expected[3]);
            assertThat(row.get("confidence_summary")).isEqualTo(entry.getValue());
        }
    }

    @Test
    void appliedRulesAcrossTwentyFourPropertiesMatchExpectedCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (int n = 1; n <= 24; n++) {
            ConfidenceResult result = service.recalculateProperty(propertyId(n));
            Object[] expected = EXPECTED[n - 1];
            assertThat(result.rawScore()).as("raw " + n).isEqualTo((Integer) expected[1]);
            assertThat(result.confidenceScore()).as("score " + n).isEqualTo((Integer) expected[2]);
            assertThat(result.confidenceLevel()).as("level " + n).isEqualTo(expected[3]);
            result.appliedRules().forEach(rule -> counts.merge(rule.ruleName(), 1, Integer::sum));
        }
        assertThat(counts)
                .containsEntry("source_units_2_plus", 16)
                .containsEntry("field_observation_bonus", 16)
                .containsEntry("verified_source_bonus", 11)
                .containsEntry("source_units_1", 8)
                .containsEntry("doc_declared_bonus", 5)
                .containsEntry("doc_partial_bonus", 5)
                .containsEntry("doc_verified_bonus", 5)
                .containsEntry("doc_litigious_penalty", 4)
                .doesNotContainKey("stale_sources_penalty");
    }

    // ---- batch -----------------------------------------------------------------------------------------------

    @Test
    void batchRecalculatesTwentyFourPropertiesWithExpectedDistribution() throws Exception {
        JsonNode data = recalcOrg(ORG_ID, 200).get("data");
        assertThat(data.get("organisationId").asText()).isEqualTo(ORG);
        assertThat(data.get("recalculatedCount").asInt()).isEqualTo(24);

        Map<Integer, Map<String, Object>> rows = pilotRows();
        assertThat(rows).hasSize(24);
        for (Object[] expected : EXPECTED) {
            int n = (Integer) expected[0];
            Map<String, Object> row = rows.get(n);
            assertThat(((Number) row.get("confidence_score")).intValue()).as("score " + n).isEqualTo((Integer) expected[2]);
            assertThat(row.get("confidence_level")).as("level " + n).isEqualTo(expected[3]);
        }
        assertThat(levelDistribution()).containsExactlyInAnyOrderEntriesOf(Map.of("medium", 14, "low", 7, "high", 3));
        SUMMARIES.forEach((n, summary) -> assertThat(rows.get(n).get("confidence_summary")).isEqualTo(summary));
    }

    @Test
    void laterClockAppliesStalePenaltyToAllTwentyFourProperties() throws Exception {
        clock.set(LATER_NOW);
        assertThat(recalcOrg(ORG_ID, 200).get("data").get("recalculatedCount").asInt()).isEqualTo(24);

        Map<Integer, Map<String, Object>> rows = pilotRows();
        for (Object[] expected : EXPECTED) {
            int n = (Integer) expected[0];
            int score = ((Number) rows.get(n).get("confidence_score")).intValue();
            assertThat(score).as("score " + n).isEqualTo(Math.max(0, (Integer) expected[1] - 5));
        }
        assertThat(((Number) rows.get(3).get("confidence_score")).intValue()).isEqualTo(25);
        assertThat(levelDistribution()).containsExactlyInAnyOrderEntriesOf(Map.of("medium", 12, "low", 9, "high", 3));

        JsonNode applied = recalcProperty(propertyId(3), 200).get("data").get("appliedRules");
        List<String> names = new ArrayList<>();
        applied.forEach(rule -> names.add(rule.get("ruleName").asText()));
        assertThat(names).contains("stale_sources_penalty");
    }

    // ---- configurable sans redéploiement ---------------------------------------------------------------------

    @Test
    void changingScoreDeltaBySqlChangesResultWithoutRedeploy() throws Exception {
        assertThat(recalcProperty(propertyId(3), 200).get("data").get("confidenceScore").asInt()).isEqualTo(30);

        jdbc.update("UPDATE confidence_rule SET score_delta = 20 WHERE organisation_id = ?::uuid"
                + " AND rule_name = 'doc_partial_bonus'", ORG);
        JsonNode data = recalcProperty(propertyId(3), 200).get("data");
        assertThat(data.get("confidenceScore").asInt()).isEqualTo(40);
        assertThat(data.get("confidenceLevel").asText()).isEqualTo("medium");
    }

    @Test
    void deactivatingRuleBySqlRemovesItsContribution() throws Exception {
        jdbc.update("UPDATE confidence_rule SET is_active = false WHERE organisation_id = ?::uuid"
                + " AND rule_name = 'source_units_1'", ORG);
        JsonNode data = recalcProperty(propertyId(3), 200).get("data");
        assertThat(data.get("confidenceScore").asInt()).isEqualTo(10);
        assertThat(data.get("confidenceLevel").asText()).isEqualTo("low");
    }

    @Test
    void changingPriorityReordersAppliedRulesButNotScore() throws Exception {
        JsonNode before = recalcProperty(propertyId(3), 200).get("data");
        assertThat(ruleNames(before)).containsExactly("source_units_1", "doc_partial_bonus");

        jdbc.update("UPDATE confidence_rule SET priority = 5 WHERE organisation_id = ?::uuid"
                + " AND rule_name = 'doc_partial_bonus'", ORG);
        JsonNode after = recalcProperty(propertyId(3), 200).get("data");
        assertThat(ruleNames(after)).containsExactly("doc_partial_bonus", "source_units_1");
        assertThat(after.get("confidenceScore").asInt()).isEqualTo(before.get("confidenceScore").asInt());
        assertThat(after.get("rawScore").asInt()).isEqualTo(before.get("rawScore").asInt());
    }

    private static List<String> ruleNames(JsonNode data) {
        List<String> names = new ArrayList<>();
        data.get("appliedRules").forEach(rule -> names.add(rule.get("ruleName").asText()));
        return names;
    }

    // ---- portée par organisation + exclusions ----------------------------------------------------------------

    @Test
    void otherOrganisationPropertyIsNotTouchedByPilotBatchAndScoresZeroOnItsOwn() throws Exception {
        UUID otherOrg = insertOrganisation();
        UUID foreign = insertProperty(otherOrg, true, false);

        assertThat(recalcOrg(ORG_ID, 200).get("data").get("recalculatedCount").asInt()).isEqualTo(24);
        Map<String, Object> untouched = jdbc.queryForMap(
                "SELECT confidence_score, confidence_level, confidence_summary FROM property WHERE id = ?", foreign);
        assertThat(((Number) untouched.get("confidence_score")).intValue()).isEqualTo(50);
        assertThat(untouched.get("confidence_level")).isEqualTo("medium");
        assertThat(untouched.get("confidence_summary")).isEqualTo("x");

        JsonNode data = recalcProperty(foreign, 200).get("data");
        assertThat(data.get("confidenceScore").asInt()).isZero();
        assertThat(data.get("confidenceLevel").asText()).isEqualTo("low");
        assertThat(data.get("confidenceSummary").asText()).isEqualTo(NO_EVIDENCE);
        assertThat(data.get("appliedRules").isArray()).isTrue();
        assertThat(data.get("appliedRules")).isEmpty();
    }

    @Test
    void softDeletedOrGeometrylessPropertiesAre404AndExcludedFromBatch() throws Exception {
        UUID deleted = insertProperty(ORG_ID, true, true);
        UUID noGeometry = insertProperty(ORG_ID, false, false);

        for (UUID id : List.of(deleted, noGeometry)) {
            JsonNode body = recalcProperty(id, 404);
            assertThat(body.get("success").asBoolean()).isFalse();
            assertThat(body.get("message").asText()).isEqualTo("PROPERTY_NOT_FOUND");
        }
        assertThat(recalcProperty(UUID.randomUUID(), 404).get("message").asText()).isEqualTo("PROPERTY_NOT_FOUND");

        assertThat(recalcOrg(ORG_ID, 200).get("data").get("recalculatedCount").asInt()).isEqualTo(24);
        for (UUID id : List.of(deleted, noGeometry)) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT confidence_score, confidence_summary FROM property WHERE id = ?", id);
            assertThat(((Number) row.get("confidence_score")).intValue()).isEqualTo(50);
            assertThat(row.get("confidence_summary")).isEqualTo("x");
        }
    }

    @Test
    void unknownOrDeletedOrganisationIs404AndEmptyOrganisationReturnsZero() throws Exception {
        assertThat(recalcOrg(UUID.randomUUID(), 404).get("message").asText()).isEqualTo("ORGANISATION_NOT_FOUND");

        UUID deletedOrg = insertOrganisation();
        jdbc.update("UPDATE organisation SET deleted_at = now() WHERE id = ?", deletedOrg);
        assertThat(recalcOrg(deletedOrg, 404).get("message").asText()).isEqualTo("ORGANISATION_NOT_FOUND");

        UUID emptyOrg = insertOrganisation();
        JsonNode data = recalcOrg(emptyOrg, 200).get("data");
        assertThat(data.get("organisationId").asText()).isEqualTo(emptyOrg.toString());
        assertThat(data.get("recalculatedCount").asInt()).isZero();
    }

    // ---- règle active invalide -------------------------------------------------------------------------------

    @Test
    void invalidActiveRuleYields500InternalErrorAndLeavesPropertiesUntouched() throws Exception {
        jdbc.update(
                "INSERT INTO confidence_rule (organisation_id, rule_name, rule_type, condition_json, score_delta,"
                        + " is_active, priority) VALUES (?::uuid, 'it_invalid_units', 'source_count',"
                        + " '{\"minUnits\":\"x\",\"maxUnits\":null}'::jsonb, 5, true, 1)",
                ORG);
        List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT id, confidence_score, confidence_level, confidence_summary, updated_at FROM property"
                        + " ORDER BY id");

        JsonNode org = recalcOrg(ORG_ID, 500);
        assertThat(org.get("success").asBoolean()).isFalse();
        assertThat(org.get("message").asText()).isEqualTo("INTERNAL_ERROR");
        JsonNode property = recalcProperty(propertyId(3), 500);
        assertThat(property.get("message").asText()).isEqualTo("INTERNAL_ERROR");

        List<Map<String, Object>> after = jdbc.queryForList(
                "SELECT id, confidence_score, confidence_level, confidence_summary, updated_at FROM property"
                        + " ORDER BY id");
        assertThat(after).isEqualTo(before);
    }

    // ---- API sources -----------------------------------------------------------------------------------------

    @Test
    void sourcesEndpointExposesWeightButNeitherPropertyIdNorVerified() throws Exception {
        ResultActions actions = mockMvc.perform(get("/api/v1/properties/{id}/sources", propertyId(1)));
        actions.andExpect(status().isOk());
        JsonNode items = json(actions).get("data");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : items) {
            assertThat(item.has("weight")).isTrue();
            assertThat(item.get("weight").asInt()).isBetween(1, 3);
            assertThat(item.has("propertyId")).isFalse();
            assertThat(item.has("verified")).isFalse();
            Iterator<String> fields = item.fieldNames();
            List<String> names = new ArrayList<>();
            fields.forEachRemaining(names::add);
            assertThat(names).contains("id", "sourceType", "weight");
        }
    }
}
