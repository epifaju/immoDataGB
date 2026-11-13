package com.immodata.gb.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.infrastructure.storage.InMemoryDocumentStorage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

/** Bloc 4 : drapeau {@code app.documents.mutations-enabled=false} : mutations en 404, lecture intacte. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentMutationsDisabledIT {

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
        registry.add("app.documents.mutations-enabled", () -> "false");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryStorageConfig {
        @Bean
        @Primary
        InMemoryDocumentStorage inMemoryDocumentStorage() {
            return new InMemoryDocumentStorage();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemoryDocumentStorage storage;

    @Test
    void mutationsReturn404WhenFlagIsFalseButListStillWorks() throws Exception {
        mockMvc.perform(multipart("/api/v1/properties/{id}/documents", PROPERTY_ID)
                        .file(new MockMultipartFile(
                                "file", "a.pdf", "application/pdf", "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII)))
                        .param("documentType", "autre"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/documents/{id}/signed-url", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/documents/{id}", UUID.randomUUID())).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/properties/{id}/documents", PROPERTY_ID)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Integer.class)).isZero();
        assertThat(storage.size()).isZero();
    }
}
