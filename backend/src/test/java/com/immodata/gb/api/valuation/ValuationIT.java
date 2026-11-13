package com.immodata.gb.api.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Bloc 5 : estimation par comparables contre PostGIS. Profil {@code dev} par défaut (seed chargé), horloge fixée au
 * 2026-11-13 (fenêtre de 24 mois : 2024-11-13 .. 2026-11-13). Chaque test est {@code @Transactional} (rollback) :
 * le seed reste intact. Cas de référence : en-tête de {@code R__seed_bissau_dev.sql} (centre 11.863 / -15.598).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ValuationIT {

    private static final double CENTER_LAT = 11.863;
    private static final double CENTER_LNG = -15.598;
    private static final String APARTMENT_ID_PREFIX = "00000000-0000-0000-0005-0000000000";

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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private ResultActions estimate(String type, int radiusMeters, int periodMonths) throws Exception {
        String body = """
                {"propertyType":"%s","areaSqm":80,"latitude":%s,"longitude":%s,"radiusMeters":%d,"periodMonths":%d}
                """
                .formatted(type, CENTER_LAT, CENTER_LNG, radiusMeters, periodMonths);
        return mockMvc.perform(post("/api/v1/valuations/estimate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Set<String> comparableIds(JsonNode data) {
        Set<String> ids = new HashSet<>();
        data.get("comparables").forEach(c -> ids.add(c.get("id").asText()));
        return ids;
    }

    @Test
    void flywayAppliesV10AndSeedsThirtySixComparables() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '10' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM comparable_observation WHERE deleted_at IS NULL", Integer.class))
                .isEqualTo(36);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM comparable_observation WHERE property_type = 'apartment'",
                        Integer.class))
                .isEqualTo(9);
    }

    @Test
    void radius5kmExcludesTwoOutliersAndReturnsMedianWithoutWarning() throws Exception {
        ResultActions actions = estimate("apartment", 5000, 24);
        actions.andExpect(status().isOk());
        JsonNode body = json(actions);
        assertThat(body.get("success").asBoolean()).isTrue();
        JsonNode data = body.get("data");

        assertThat(data.get("currency").asText()).isEqualTo("XOF");
        assertThat(data.get("comparablesRawCount").asInt()).isEqualTo(9);
        assertThat(data.get("comparablesCount").asInt()).isEqualTo(7);
        assertThat(data.get("comparables")).hasSize(7);
        assertThat(comparableIds(data))
                .doesNotContain(APARTMENT_ID_PREFIX + "17", APARTMENT_ID_PREFIX + "18");
        assertThat(data.get("warning").isNull()).isTrue();

        assertThat(data.get("medianPricePerSqm").decimalValue()).isEqualByComparingTo(new BigDecimal("420000"));
        long median = data.get("medianTotal").asLong();
        long low = data.get("rangeLow").asLong();
        long high = data.get("rangeHigh").asLong();
        assertThat(median).isEqualTo(33_600_000L);
        assertThat(low).isLessThanOrEqualTo(median);
        assertThat(median).isLessThanOrEqualTo(high);
        assertThat(low).isLessThan(high);
        assertThat(data.get("rangeLowPricePerSqm").decimalValue())
                .isLessThanOrEqualTo(data.get("medianPricePerSqm").decimalValue());
        assertThat(data.get("rangeHighPricePerSqm").decimalValue())
                .isGreaterThanOrEqualTo(data.get("medianPricePerSqm").decimalValue());
        assertThat(data.get("confidenceScore").asInt()).isBetween(0, 100);
        assertThat(data.get("confidenceLevel").asText()).isIn("low", "medium", "high");
    }

    @Test
    void radius500mYieldsThreeComparablesWithWarning() throws Exception {
        ResultActions actions = estimate("apartment", 500, 24);
        actions.andExpect(status().isOk());
        JsonNode data = json(actions).get("data");

        assertThat(data.get("comparablesRawCount").asInt()).isEqualTo(3);
        assertThat(data.get("comparablesCount").asInt()).isEqualTo(3);
        assertThat(data.get("warning").isNull()).isFalse();
        assertThat(data.get("warning").asText()).isNotBlank();
        assertThat(data.get("medianPricePerSqm").decimalValue()).isEqualByComparingTo(new BigDecimal("420000"));
        assertThat(data.get("rangeLow").asLong()).isLessThanOrEqualTo(data.get("medianTotal").asLong());
        assertThat(data.get("rangeHigh").asLong()).isGreaterThanOrEqualTo(data.get("medianTotal").asLong());
    }

    @Test
    void radius100mHasNoCandidatesAndReturnsNotFound() throws Exception {
        ResultActions actions = estimate("apartment", 100, 24);
        actions.andExpect(status().isNotFound());
        JsonNode body = json(actions);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).isEqualTo("VALUATION_NO_COMPARABLES");
    }

    @Test
    void shortPeriodExcludesOlderObservations() throws Exception {
        // 1 mois (depuis 2026-10-13) : aucun appartement du seed (le plus récent : 2026-10-05).
        ResultActions actions = estimate("apartment", 5000, 1);
        actions.andExpect(status().isNotFound());
        assertThat(json(actions).get("message").asText()).isEqualTo("VALUATION_NO_COMPARABLES");
    }

    @Test
    void softDeletedObservationIsExcluded() throws Exception {
        assertThat(jdbc.update(
                        "UPDATE comparable_observation SET deleted_at = now() WHERE id = ?::uuid",
                        APARTMENT_ID_PREFIX + "10"))
                .isEqualTo(1);

        JsonNode data = json(estimate("apartment", 5000, 24).andExpect(status().isOk())).get("data");
        assertThat(data.get("comparablesRawCount").asInt()).isEqualTo(8);
        assertThat(comparableIds(data)).doesNotContain(APARTMENT_ID_PREFIX + "10");

        // Rayon 500 m : il ne reste que 2 candidats (11, 12), jamais l'observation supprimée.
        JsonNode narrow = json(estimate("apartment", 500, 24).andExpect(status().isOk())).get("data");
        assertThat(narrow.get("comparablesRawCount").asInt()).isEqualTo(2);
        assertThat(comparableIds(narrow)).doesNotContain(APARTMENT_ID_PREFIX + "10");
        assertThat(narrow.get("warning").isNull()).isFalse();
    }
}
