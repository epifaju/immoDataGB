package com.immodata.gb.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgisHealthIT {

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

    @Test
    void flywayEnablesPostgisAndHealthReturnsVersion() throws Exception {
        List<String> extensions = jdbc.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('postgis', 'pg_trgm')",
                String.class);
        assertThat(extensions).containsExactlyInAnyOrder("postgis", "pg_trgm");

        ResponseEntity<String> response = rest.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("data").get("api").asText()).isEqualTo("up");
        assertThat(body.get("data").get("database").asText()).isEqualTo("up");
        assertThat(body.get("data").get("postgis").asText()).isNotBlank();
        assertThat(body.get("message").isNull()).isTrue();
        assertThat(body.get("meta").size()).isZero();
    }
}
