package com.immodata.gb.api.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Bloc 6.2 : création terrain contre PostGIS. Pending exclu de la liste spatiale ; détail GET OK.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class FieldObservationIT {

    private static final String DEFAULT_BBOX = "-15.70,11.80,-15.50,11.95";
    private static final String BODY =
            """
            {"latitude":11.86,"longitude":-15.60,"propertyType":"house",
             "title":"Campo IT","occupationStatus":"vacant","notes":"teste IT"}
            """;

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
        registry.add("app.field.mutations-enabled", () -> "true");
        registry.add("app.field.organisation-id", () -> "00000000-0000-0000-0000-00000000000a");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createdId;

    @AfterEach
    void cleanUp() {
        if (createdId == null) {
            return;
        }
        jdbc.update("DELETE FROM source WHERE property_id = ?", createdId);
        jdbc.update("DELETE FROM property_geometry WHERE property_id = ?", createdId);
        jdbc.update("DELETE FROM property WHERE id = ?", createdId);
        createdId = null;
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void createPendingHiddenFromSpatialListButDetailAvailable() throws Exception {
        ResultActions create = mockMvc.perform(post("/api/v1/field-observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.validationStatus").value("pending_validation"))
                .andExpect(jsonPath("$.data.confidenceScore").value(12))
                .andExpect(jsonPath("$.data.confidenceLevel").value("low"));

        JsonNode data = json(create).get("data");
        createdId = UUID.fromString(data.get("id").asText());
        String reference = data.get("reference").asText();
        assertThat(reference).startsWith("FLD-");

        assertThat(jdbc.queryForObject(
                        "SELECT validation_status FROM property WHERE id = ?", String.class, createdId))
                .isEqualTo("pending_validation");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM property_geometry
                        WHERE property_id = ? AND geom_type = 'point'
                        """,
                        Integer.class,
                        createdId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM source
                        WHERE property_id = ? AND source_type = 'field_observation'
                        """,
                        Integer.class,
                        createdId))
                .isEqualTo(1);

        JsonNode list = json(mockMvc.perform(get("/api/v1/properties")
                        .param("bbox", DEFAULT_BBOX)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk()));
        list.get("data")
                .get("items")
                .forEach(n -> assertThat(n.get("id").asText()).isNotEqualTo(createdId.toString()));
        assertThat(list.get("data").get("items").toString()).contains("BIS-001");

        mockMvc.perform(get("/api/v1/properties/{id}", createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference").value(reference));
    }
}
