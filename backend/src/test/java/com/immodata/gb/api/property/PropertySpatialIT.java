package com.immodata.gb.api.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PropertySpatialIT {

    private static final String DEFAULT_BBOX = "-15.70,11.80,-15.50,11.95";

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
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void seedLoadedAndCoordinatesPlausible() {
        Integer orgs = jdbc.queryForObject("SELECT count(*) FROM organisation", Integer.class);
        Integer props = jdbc.queryForObject("SELECT count(*) FROM property", Integer.class);
        Integer geoms = jdbc.queryForObject("SELECT count(*) FROM property_geometry", Integer.class);
        assertThat(orgs).isEqualTo(1);
        assertThat(props).isEqualTo(24);
        assertThat(geoms).isEqualTo(24);

        Integer badLat = jdbc.queryForObject(
                """
                SELECT count(*) FROM property_geometry
                WHERE ST_Y(geom) < 11 OR ST_Y(geom) > 12
                   OR ST_X(geom) < -16 OR ST_X(geom) > -15
                """,
                Integer.class);
        assertThat(badLat).isZero();
    }

    @Test
    void defaultBboxPaginationIsStable() throws Exception {
        JsonNode page0 = getProperties(DEFAULT_BBOX, null, null, 0, 20);
        assertThat(page0.get("data").get("totalElements").asInt()).isEqualTo(24);
        assertThat(page0.get("data").get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("data").get("items").size()).isEqualTo(20);
        assertThat(page0.get("data").get("items").get(0).get("reference").asText()).isEqualTo("BIS-024");
        assertThat(page0.get("data").get("items").get(19).get("reference").asText()).isEqualTo("BIS-005");

        JsonNode page1 = getProperties(DEFAULT_BBOX, null, null, 1, 20);
        assertThat(page1.get("data").get("items").size()).isEqualTo(4);
        assertThat(page1.get("data").get("items").get(0).get("reference").asText()).isEqualTo("BIS-004");
        assertThat(page1.get("data").get("items").get(3).get("reference").asText()).isEqualTo("BIS-001");

        Set<String> refs = new HashSet<>();
        page0.get("data").get("items").forEach(n -> refs.add(n.get("reference").asText()));
        page1.get("data").get("items").forEach(n -> assertThat(refs.add(n.get("reference").asText())).isTrue());
    }

    @Test
    void sizeBounds() throws Exception {
        JsonNode all = getProperties(DEFAULT_BBOX, null, null, 0, 100);
        assertThat(all.get("data").get("items").size()).isEqualTo(24);

        ResponseEntity<String> tooBig =
                rest.getForEntity("/api/v1/properties?bbox={bbox}&size=101", String.class, DEFAULT_BBOX);
        assertThat(tooBig.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(tooBig.getBody()).get("message").asText())
                .isEqualTo("PROPERTY_PAGE_INVALID");
    }

    @Test
    void emptyAndSubBboxAndEdge() throws Exception {
        JsonNode empty = getProperties("-15.40,11.80,-15.30,11.95", null, null, 0, 20);
        assertThat(empty.get("data").get("totalElements").asInt()).isZero();
        assertThat(empty.get("data").get("totalPages").asInt()).isZero();
        assertThat(empty.get("data").get("items").size()).isZero();

        JsonNode sub = getProperties("-15.70,11.80,-15.60,11.95", null, null, 0, 100);
        assertThat(sub.get("data").get("totalElements").asInt()).isEqualTo(12);

        JsonNode edge = getProperties("-15.70,11.80,-15.616,11.95", null, null, 0, 100);
        assertThat(edge.get("data").get("totalElements").asInt()).isEqualTo(12);
    }

    @Test
    void filters() throws Exception {
        assertThat(getProperties(DEFAULT_BBOX, "house", null, 0, 100)
                        .get("data")
                        .get("totalElements")
                        .asInt())
                .isEqualTo(6);
        assertThat(getProperties(DEFAULT_BBOX, null, "high", 0, 100)
                        .get("data")
                        .get("totalElements")
                        .asInt())
                .isEqualTo(8);
        assertThat(getProperties(DEFAULT_BBOX, "house", "high", 0, 100)
                        .get("data")
                        .get("totalElements")
                        .asInt())
                .isEqualTo(2);
    }

    @Test
    void softDeletedExcludedAndNotFound() {
        UUID deletedId = UUID.fromString("00000000-0000-0000-0001-00000000dead");
        UUID geomId = UUID.fromString("00000000-0000-0000-0002-00000000dead");
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.update(
                    """
                    INSERT INTO property (
                      id, organisation_id, reference, title, property_type,
                      confidence_score, confidence_level, confidence_summary, deleted_at
                    ) VALUES (?, '00000000-0000-0000-0000-00000000000a', 'BIS-DEL', 'Deleted',
                              'house', 12, 'low', 'gone', now())
                    """,
                    deletedId);
            jdbc.update(
                    """
                    INSERT INTO property_geometry (id, property_id, geom, geom_type, precision_level)
                    VALUES (?, ?, ST_SetSRID(ST_MakePoint(-15.60, 11.86), 4326), 'point', 'gps')
                    """,
                    geomId,
                    deletedId);

            JsonNode list;
            try {
                list = getProperties(DEFAULT_BBOX, null, null, 0, 100);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            assertThat(list.get("data").get("totalElements").asInt()).isEqualTo(24);

            ResponseEntity<String> detail =
                    rest.getForEntity("/api/v1/properties/{id}", String.class, deletedId);
            assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ResponseEntity<String> sources =
                    rest.getForEntity("/api/v1/properties/{id}/sources", String.class, deletedId);
            assertThat(sources.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            status.setRollbackOnly();
        });
    }

    @Test
    void detailAndSourcesForSeededProperties() throws Exception {
        ResponseEntity<String> one = rest.getForEntity(
                "/api/v1/properties/{id}", String.class, "00000000-0000-0000-0001-000000000001");
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode dataOne = objectMapper.readTree(one.getBody()).get("data");
        assertThat(dataOne.get("docStatus").asText()).isEqualTo("none");
        assertThat(dataOne.get("addressText").asText()).isNotBlank();
        assertThat(dataOne.get("confidenceSummary").asText()).isNotBlank();

        ResponseEntity<String> three = rest.getForEntity(
                "/api/v1/properties/{id}", String.class, "00000000-0000-0000-0001-000000000003");
        assertThat(objectMapper.readTree(three.getBody()).get("data").get("addressText").isNull())
                .isTrue();

        ResponseEntity<String> sourcesFive = rest.getForEntity(
                "/api/v1/properties/{id}/sources",
                String.class,
                "00000000-0000-0000-0001-000000000005");
        assertThat(sourcesFive.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sources = objectMapper.readTree(sourcesFive.getBody()).get("data");
        assertThat(sources.size()).isEqualTo(3);
        assertThat(sources.get(0).get("sourceType").asText()).isEqualTo("field_observation");
        assertThat(sources.get(1).get("sourceType").asText()).isEqualTo("agency_listing");
        assertThat(sources.get(2).get("sourceType").asText()).isEqualTo("institutional");
        assertThat(sources.get(2).get("label").isNull()).isTrue();
        assertThat(sources.get(0).get("weight").asInt()).isEqualTo(2);
        assertThat(sources.get(1).get("weight").asInt()).isEqualTo(1);
        assertThat(sources.get(2).get("weight").asInt()).isEqualTo(3);
        for (JsonNode source : sources) {
            assertThat(source.has("propertyId")).isFalse();
            assertThat(source.has("verified")).isFalse();
        }

        Integer sourceCount = jdbc.queryForObject("SELECT count(*) FROM source", Integer.class);
        assertThat(sourceCount).isEqualTo(48);
        Integer verified = jdbc.queryForObject(
                "SELECT count(*) FROM source WHERE is_verified = true", Integer.class);
        assertThat(verified).isEqualTo(11);

        ResponseEntity<String> missing = rest.getForEntity(
                "/api/v1/properties/{id}", String.class, "00000000-0000-0000-0001-000000000099");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listItemsOmitDetailFields() throws Exception {
        JsonNode page0 = getProperties(DEFAULT_BBOX, null, null, 0, 20);
        assertThat(page0.get("data").get("totalElements").asInt()).isEqualTo(24);
        assertThat(page0.get("data").get("items").get(0).has("docStatus")).isFalse();
        assertThat(page0.get("data").get("items").get(0).has("addressText")).isFalse();
    }

    @Test
    void propertyWithoutSourcesReturnsEmptyList() throws Exception {
        UUID bareId = UUID.fromString("00000000-0000-0000-0001-00000000babe");
        UUID geomId = UUID.fromString("00000000-0000-0000-0002-00000000babe");
        jdbc.update(
                """
                INSERT INTO property (
                  id, organisation_id, reference, title, property_type,
                  confidence_score, confidence_level, confidence_summary, doc_status
                ) VALUES (?, '00000000-0000-0000-0000-00000000000a', 'BIS-BARE', 'Bare',
                          'house', 12, 'low', 'none', 'none')
                """,
                bareId);
        jdbc.update(
                """
                INSERT INTO property_geometry (id, property_id, geom, geom_type, precision_level)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(-15.60, 11.86), 4326), 'point', 'gps')
                """,
                geomId,
                bareId);
        try {
            ResponseEntity<String> sources =
                    rest.getForEntity("/api/v1/properties/{id}/sources", String.class, bareId);
            assertThat(sources.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(objectMapper.readTree(sources.getBody()).get("data").size()).isZero();
        } finally {
            jdbc.update("DELETE FROM property_geometry WHERE id = ?", geomId);
            jdbc.update("DELETE FROM property WHERE id = ?", bareId);
        }
    }

    @Test
    void confidenceCheckRejectsIncoherentPair() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO property (
                          id, organisation_id, reference, title, property_type,
                          confidence_score, confidence_level, confidence_summary
                        ) VALUES (
                          '00000000-0000-0000-0001-00000000bad1',
                          '00000000-0000-0000-0000-00000000000a',
                          'BIS-BAD', 'Bad', 'house', 90, 'low', 'incoherent'
                        )
                        """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JsonNode getProperties(String bbox, String type, String confidence, int page, int size)
            throws Exception {
        StringBuilder url = new StringBuilder("/api/v1/properties?bbox={bbox}&page={page}&size={size}");
        if (type != null) {
            url.append("&type={type}");
        }
        if (confidence != null) {
            url.append("&confidence={confidence}");
        }
        ResponseEntity<String> response;
        if (type != null && confidence != null) {
            response = rest.getForEntity(url.toString(), String.class, bbox, page, size, type, confidence);
        } else if (type != null) {
            response = rest.getForEntity(url.toString(), String.class, bbox, page, size, type);
        } else if (confidence != null) {
            response = rest.getForEntity(url.toString(), String.class, bbox, page, size, confidence);
        } else {
            response = rest.getForEntity(url.toString(), String.class, bbox, page, size);
        }
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }
}
