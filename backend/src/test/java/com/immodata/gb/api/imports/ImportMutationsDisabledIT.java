package com.immodata.gb.api.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Bloc 6 : {@code app.imports.mutations-enabled=false} : POST en 404, lecture intacte, aucun job créé. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImportMutationsDisabledIT {

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
        registry.add("app.imports.mutations-enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postReturns404WhenFlagIsFalseButListStillWorks() throws Exception {
        byte[] content = new ClassPathResource("imports/sample-mixed.csv").getContentAsByteArray();
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "sample-mixed.csv", "text/csv", content)))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile(
                                "file", "empty.csv", "text/csv", "".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/imports")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_job", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property WHERE reference = 'IMP-IT-001'", Integer.class))
                .isZero();
    }
}
