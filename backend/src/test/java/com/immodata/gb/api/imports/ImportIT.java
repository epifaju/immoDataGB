package com.immodata.gb.api.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Bloc 6 : import CSV contre PostGIS (profil {@code dev}, mutations activées). Le job Spring Batch tourne dans un
 * autre thread et une autre connexion : ces tests ne sont donc pas {@code @Transactional} ; les données créées sont
 * nettoyées après chaque test.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ImportIT {

    private static final String NEW_REFERENCE = "IMP-IT-001";
    private static final long POLL_TIMEOUT_MS = 15_000;
    private static final long POLL_INTERVAL_MS = 200;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("immodata")
            .withUsername("immodata")
            .withPassword("immodata")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.imports.mutations-enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM source WHERE property_id IN (SELECT id FROM property WHERE reference = ?)", NEW_REFERENCE);
        jdbc.update(
                "DELETE FROM property_geometry WHERE property_id IN (SELECT id FROM property WHERE reference = ?)",
                NEW_REFERENCE);
        jdbc.update("DELETE FROM property WHERE reference = ?", NEW_REFERENCE);
        jdbc.update("DELETE FROM import_job");
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static byte[] sampleMixed() throws IOException {
        return new ClassPathResource("imports/sample-mixed.csv").getContentAsByteArray();
    }

    private ResultActions upload(String filename, byte[] content) throws Exception {
        return mockMvc.perform(
                multipart("/api/v1/imports").file(new MockMultipartFile("file", filename, "text/csv", content)));
    }

    private JsonNode pollUntilFinished(UUID jobId) throws Exception {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        JsonNode data = null;
        while (System.currentTimeMillis() < deadline) {
            ResultActions actions =
                    mockMvc.perform(get("/api/v1/imports/{id}/status", jobId)).andExpect(status().isOk());
            data = json(actions).get("data");
            String state = data.get("status").asText();
            if ("completed".equals(state) || "failed".equals(state)) {
                return data;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError("Import job " + jobId + " did not finish in time; last status: " + data);
    }

    private int countJobs() {
        return jdbc.queryForObject("SELECT count(*) FROM import_job", Integer.class);
    }

    // ---- tests -----------------------------------------------------------------------------------------------

    @Test
    void mixedCsvImportsOneRowAndReportsTwoErrors() throws Exception {
        ResultActions started = upload("sample-mixed.csv", sampleMixed());
        started.andExpect(status().isAccepted());
        JsonNode start = json(started).get("data");
        assertThat(start.get("status").asText()).isEqualTo("queued");
        UUID jobId = UUID.fromString(start.get("jobId").asText());

        JsonNode finished = pollUntilFinished(jobId);
        assertThat(finished.get("status").asText()).isEqualTo("completed");
        assertThat(finished.get("totalRows").asInt()).isEqualTo(3);
        assertThat(finished.get("successRows").asInt()).isEqualTo(1);
        assertThat(finished.get("errorRows").asInt()).isEqualTo(2);

        Map<String, Object> job =
                jdbc.queryForMap("SELECT status, success_rows, error_rows FROM import_job WHERE id = ?", jobId);
        assertThat(job.get("status")).isEqualTo("completed");
        assertThat(job.get("success_rows")).isEqualTo(1);
        assertThat(job.get("error_rows")).isEqualTo(2);

        Map<String, Object> property = jdbc.queryForMap(
                "SELECT id, property_type, confidence_score, confidence_level, deleted_at"
                        + " FROM property WHERE reference = ?",
                NEW_REFERENCE);
        assertThat(property.get("property_type")).isEqualTo("apartment");
        assertThat(property.get("confidence_score")).isEqualTo(12);
        assertThat(property.get("confidence_level")).isEqualTo("low");
        assertThat(property.get("deleted_at")).isNull();
        UUID propertyId = (UUID) property.get("id");

        Boolean geomOk = jdbc.queryForObject(
                "SELECT ST_SRID(geom) = 4326 AND ST_X(geom) = -15.5977 AND ST_Y(geom) = 11.8636"
                        + " FROM property_geometry WHERE property_id = ?",
                Boolean.class,
                propertyId);
        assertThat(geomOk).isTrue();

        List<String> sourceTypes =
                jdbc.queryForList("SELECT source_type FROM source WHERE property_id = ?", String.class, propertyId);
        assertThat(sourceTypes).containsExactly("csv_import");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM property WHERE reference IN ('IMP-IT-002')", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM property WHERE reference = 'BIS-001' AND deleted_at IS NULL",
                        Integer.class))
                .isEqualTo(1);

        JsonNode errors = json(mockMvc.perform(get("/api/v1/imports/{id}/report", jobId))
                        .andExpect(status().isOk()))
                .get("data")
                .get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).get("code").asText()).isEqualTo("IMPORT_INVALID_PROPERTY_TYPE");
        assertThat(errors.get(1).get("code").asText()).isEqualTo("IMPORT_DUPLICATE_REFERENCE");
    }

    @Test
    void invalidHeaderReturns400AndCreatesNoJob() throws Exception {
        int before = countJobs();
        byte[] badHeader = "ref,title,property_type,address_text,latitude,longitude\nX-1,t,house,a,11.8,-15.5\n"
                .getBytes(StandardCharsets.UTF_8);

        ResultActions actions = upload("bad.csv", badHeader);
        actions.andExpect(status().isBadRequest());
        assertThat(json(actions).get("message").asText()).isEqualTo("IMPORT_INVALID_HEADER");

        assertThat(countJobs()).isEqualTo(before);
    }
}
