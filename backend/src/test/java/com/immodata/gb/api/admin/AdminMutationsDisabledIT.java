package com.immodata.gb.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Bloc 7 : {@code app.admin.mutations-enabled=false} → POST 404, file et journal restent lisibles. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMutationsDisabledIT {

    private static final UUID PROPERTY_ID = UUID.fromString("00000000-0000-0000-0001-000000000001");

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
        registry.add("app.admin.mutations-enabled", () -> "false");
        registry.add("app.field.mutations-enabled", () -> "false");
        registry.add("app.field.organisation-id", () -> "00000000-0000-0000-0000-00000000000a");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postsReturn404WhenFlagIsFalseButReadsStayOpen() throws Exception {
        int propertiesBefore = jdbc.queryForObject("SELECT count(*) FROM property", Integer.class);
        int auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit_event", Integer.class);

        mockMvc.perform(post("/api/v1/admin/properties/{id}/validate", PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));

        mockMvc.perform(post("/api/v1/admin/properties/{id}/reject", PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/admin/validation-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM property", Integer.class)).isEqualTo(propertiesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event", Integer.class)).isEqualTo(auditBefore);
    }
}
