package com.immodata.gb.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
 * Bloc 7 : valider publie dans la liste spatiale ; rejeter retire de la file et du détail.
 * Les deux actions laissent une ligne dans {@code audit_event}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminValidationIT {

    private static final String DEFAULT_BBOX = "-15.70,11.80,-15.50,11.95";
    private static final String BODY_A =
            """
            {"latitude":11.86,"longitude":-15.60,"propertyType":"house",
             "title":"Campo IT A","occupationStatus":"vacant","notes":"teste validate"}
            """;
    private static final String BODY_B =
            """
            {"latitude":11.87,"longitude":-15.59,"propertyType":"land",
             "title":"Campo IT B","occupationStatus":"occupied","notes":"teste reject"}
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
        registry.add("app.admin.mutations-enabled", () -> "true");
        registry.add("app.field.mutations-enabled", () -> "true");
        registry.add("app.field.organisation-id", () -> "00000000-0000-0000-0000-00000000000a");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : createdIds) {
            jdbc.update("DELETE FROM source WHERE property_id = ?", id);
            jdbc.update("DELETE FROM property_geometry WHERE property_id = ?", id);
            jdbc.update("DELETE FROM audit_event WHERE entity_id = ?", id);
            jdbc.update("DELETE FROM property WHERE id = ?", id);
        }
        createdIds.clear();
    }

    @Test
    void validatePublishesAndRejectSoftDeletesWithAudit() throws Exception {
        Created pendingA = postPending(BODY_A);
        assertThat(itemField(queue(), "id")).contains(pendingA.id().toString());

        mockMvc.perform(post("/api/v1/admin/properties/{id}/validate", pendingA.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(pendingA.id().toString()))
                .andExpect(jsonPath("$.data.reference").value(pendingA.reference()));

        assertThat(itemField(spatialList(), "reference")).contains(pendingA.reference());
        assertThat(itemField(audit("validate"), "entityId")).contains(pendingA.id().toString());

        Created pendingB = postPending(BODY_B);
        assertThat(itemField(queue(), "id")).contains(pendingB.id().toString());

        mockMvc.perform(post("/api/v1/admin/properties/{id}/reject", pendingB.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(pendingB.id().toString()))
                .andExpect(jsonPath("$.data.deleted").value(true));

        assertThat(itemField(queue(), "id")).doesNotContain(pendingB.id().toString());
        assertThat(itemField(spatialList(), "reference")).doesNotContain(pendingB.reference());
        assertThat(itemField(spatialList(), "id")).doesNotContain(pendingB.id().toString());

        mockMvc.perform(get("/api/v1/properties/{id}", pendingB.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));

        assertThat(itemField(audit("reject"), "entityId")).contains(pendingB.id().toString());
    }

    private Created postPending(String body) throws Exception {
        ResultActions create = mockMvc.perform(post("/api/v1/field-observations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.validationStatus").value("pending_validation"));
        JsonNode data = json(create).get("data");
        UUID id = UUID.fromString(data.get("id").asText());
        createdIds.add(id);
        return new Created(id, data.get("reference").asText());
    }

    private JsonNode queue() throws Exception {
        return json(mockMvc.perform(get("/api/v1/admin/validation-queue").param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
    }

    private JsonNode spatialList() throws Exception {
        return json(mockMvc.perform(get("/api/v1/properties")
                        .param("bbox", DEFAULT_BBOX)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
    }

    private JsonNode audit(String action) throws Exception {
        return json(mockMvc.perform(get("/api/v1/admin/audit")
                        .param("action", action)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> itemField(JsonNode page, String field) {
        List<String> values = new ArrayList<>();
        page.get("data").get("items").forEach(item -> values.add(item.get(field).asText()));
        return values;
    }

    private record Created(UUID id, String reference) {}
}
