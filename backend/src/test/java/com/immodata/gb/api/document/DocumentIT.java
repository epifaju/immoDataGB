package com.immodata.gb.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.immodata.gb.infrastructure.config.DocumentProperties;
import com.immodata.gb.infrastructure.storage.InMemoryDocumentStorage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
import org.springframework.mock.web.MockMultipartFile;
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

/**
 * Bloc 4 : API documents contre PostGIS avec stockage objet en mémoire (pas de MinIO). Profil {@code dev} par
 * défaut (mutations activées explicitement). Chaque test est {@code @Transactional} (rollback) ; les violations de
 * contraintes tournent en {@code REQUIRES_NEW}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentIT {

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PROPERTY_ID = UUID.fromString("00000000-0000-0000-0001-000000000001");
    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n"
                    .getBytes(StandardCharsets.US_ASCII);

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
        registry.add("app.documents.mutations-enabled", () -> "true");
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
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryDocumentStorage storage;

    @Autowired
    private DocumentProperties documentProperties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetStorage() {
        storage.clear();
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions upload(UUID propertyId, String type, String filename) throws Exception {
        return mockMvc.perform(multipart("/api/v1/properties/{id}/documents", propertyId)
                .file(new MockMultipartFile("file", filename, "application/pdf", PDF_BYTES))
                .param("documentType", type));
    }

    private JsonNode uploadOk(UUID propertyId) throws Exception {
        ResultActions actions = upload(propertyId, "titre_foncier", "Titre foncier 2026.pdf");
        actions.andExpect(status().isCreated());
        return json(actions).get("data");
    }

    private void assertViolation(String sql, Object... args) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> template.executeWithoutResult(s -> jdbc.update(sql, args)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertProperty(boolean withGeometry, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO property (id, organisation_id, reference, title, property_type, confidence_score,"
                        + " confidence_level, confidence_summary, deleted_at)"
                        + " VALUES (?, ?, ?, 't', 'house', 50, 'medium', 'x', CASE WHEN ? THEN now() END)",
                id, ORG_ID, "DOC-IT-" + id, deleted);
        if (withGeometry) {
            jdbc.update(
                    "INSERT INTO property_geometry (property_id, geom, geom_type)"
                            + " VALUES (?, ST_SetSRID(ST_MakePoint(-15.6, 11.86), 4326), 'point')",
                    id);
        }
        return id;
    }

    private static final String INSERT_DOCUMENT =
            "INSERT INTO document (organisation_id, property_id, document_type, original_filename, content_type,"
                    + " size_bytes, storage_bucket, storage_key) VALUES (?, ?, ?, 'a.pdf', 'application/pdf', ?,"
                    + " 'documents', ?)";

    // ---- schéma V9 -------------------------------------------------------------------------------------------

    @Test
    void flywayAppliesV9AndCreatesEmptyDocumentTable() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '9' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Integer.class)).isZero();
    }

    @Test
    void invalidDocumentTypeIsRejectedByCheck() {
        assertViolation(INSERT_DOCUMENT, ORG_ID, PROPERTY_ID, "bogus_type", 10L, "it/bad-type-" + UUID.randomUUID());
    }

    @Test
    void sizeOutOfRangeIsRejectedByCheck() {
        assertViolation(INSERT_DOCUMENT, ORG_ID, PROPERTY_ID, "autre", 0L, "it/size-0-" + UUID.randomUUID());
        assertViolation(INSERT_DOCUMENT, ORG_ID, PROPERTY_ID, "autre", -1L, "it/size-neg-" + UUID.randomUUID());
        assertViolation(
                INSERT_DOCUMENT, ORG_ID, PROPERTY_ID, "autre", 52_428_801L, "it/size-big-" + UUID.randomUUID());
    }

    @Test
    void boundarySizesAreAccepted() {
        assertThat(jdbc.update(INSERT_DOCUMENT, ORG_ID, PROPERTY_ID, "autre", 1L, "it/size-1-" + UUID.randomUUID()))
                .isEqualTo(1);
        assertThat(jdbc.update(
                        INSERT_DOCUMENT, ORG_ID, PROPERTY_ID, "autre", 52_428_800L, "it/size-max-" + UUID.randomUUID()))
                .isEqualTo(1);
    }

    // ---- flux HTTP -------------------------------------------------------------------------------------------

    @Test
    void uploadListSignedUrlDeleteLifecycle() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/properties/{id}/documents", PROPERTY_ID))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .contains("\"data\":[]");

        JsonNode created = uploadOk(PROPERTY_ID);
        UUID documentId = UUID.fromString(created.get("id").asText());
        assertThat(created.get("propertyId").asText()).isEqualTo(PROPERTY_ID.toString());
        assertThat(created.get("documentType").asText()).isEqualTo("titre_foncier");
        assertThat(created.get("originalFilename").asText()).isEqualTo("Titre foncier 2026.pdf");
        assertThat(created.get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(created.get("sizeBytes").asLong()).isEqualTo(PDF_BYTES.length);
        assertThat(created.has("storageKey")).isFalse();
        assertThat(created.has("storageBucket")).isFalse();

        // Métadonnées en base + objet en stockage
        var row = jdbc.queryForMap(
                "SELECT organisation_id, storage_bucket, storage_key, size_bytes, deleted_at FROM document"
                        + " WHERE id = ?",
                documentId);
        assertThat(row.get("organisation_id").toString()).isEqualTo(ORG_ID.toString());
        assertThat(row.get("deleted_at")).isNull();
        String bucket = (String) row.get("storage_bucket");
        String key = (String) row.get("storage_key");
        assertThat(bucket).isEqualTo(documentProperties.bucket());
        assertThat(key).startsWith(ORG_ID + "/" + PROPERTY_ID + "/" + documentId + "/").endsWith(".pdf");
        assertThat(storage.contains(bucket, key)).isTrue();
        assertThat(storage.get(bucket, key)).isEqualTo(PDF_BYTES);
        assertThat(storage.contentType(bucket, key)).isEqualTo("application/pdf");

        // Liste
        JsonNode list = json(mockMvc.perform(get("/api/v1/properties/{id}/documents", PROPERTY_ID))
                        .andExpect(status().isOk()))
                .get("data");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(documentId.toString());

        // URL signée
        JsonNode signed = json(mockMvc.perform(post("/api/v1/documents/{id}/signed-url", documentId))
                        .andExpect(status().isOk()))
                .get("data");
        assertThat(signed.get("url").asText()).contains(bucket + "/" + key).contains("expires=900");
        assertThat(signed.get("expiresAt").asText()).isNotBlank();

        // Suppression logique : ligne conservée, objet conservé
        mockMvc.perform(delete("/api/v1/documents/{id}", documentId)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM document WHERE id = ?", Boolean.class, documentId))
                .isTrue();
        assertThat(storage.contains(bucket, key)).isTrue();

        // Liste vide ; URL signée sur document supprimé = 404
        assertThat(json(mockMvc.perform(get("/api/v1/properties/{id}/documents", PROPERTY_ID))
                                .andExpect(status().isOk()))
                        .get("data"))
                .isEmpty();
        JsonNode afterSigned = json(mockMvc.perform(post("/api/v1/documents/{id}/signed-url", documentId))
                .andExpect(status().isNotFound()));
        assertThat(afterSigned.get("message").asText()).isEqualTo("DOCUMENT_NOT_FOUND");
    }

    @Test
    void secondDeleteReturnsDocumentNotFound() throws Exception {
        UUID documentId = UUID.fromString(uploadOk(PROPERTY_ID).get("id").asText());
        mockMvc.perform(delete("/api/v1/documents/{id}", documentId)).andExpect(status().isOk());

        JsonNode body = json(mockMvc.perform(delete("/api/v1/documents/{id}", documentId))
                .andExpect(status().isNotFound()));
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).isEqualTo("DOCUMENT_NOT_FOUND");
    }

    @Test
    void unknownDocumentIs404AndMalformedIdIs400() throws Exception {
        JsonNode body = json(mockMvc.perform(delete("/api/v1/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound()));
        assertThat(body.get("message").asText()).isEqualTo("DOCUMENT_NOT_FOUND");
        mockMvc.perform(delete("/api/v1/documents/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectedUploadsLeaveNoRowNorObject() throws Exception {
        JsonNode badType = json(upload(PROPERTY_ID, "bogus", "a.pdf").andExpect(status().isBadRequest()));
        assertThat(badType.get("message").asText()).isEqualTo("DOCUMENT_TYPE_INVALID");

        // extension .pdf mais contenu non PDF (MIME Tika)
        JsonNode badMime = json(mockMvc.perform(multipart("/api/v1/properties/{id}/documents", PROPERTY_ID)
                        .file(new MockMultipartFile(
                                "file", "fake.pdf", "application/pdf", "just plain text".getBytes(StandardCharsets.UTF_8)))
                        .param("documentType", "autre"))
                .andExpect(status().isBadRequest()));
        assertThat(badMime.get("message").asText()).isEqualTo("DOCUMENT_FILE_TYPE_REJECTED");

        JsonNode badExt = json(mockMvc.perform(multipart("/api/v1/properties/{id}/documents", PROPERTY_ID)
                        .file(new MockMultipartFile("file", "run.exe", "application/octet-stream", PDF_BYTES))
                        .param("documentType", "autre"))
                .andExpect(status().isBadRequest()));
        assertThat(badExt.get("message").asText()).isEqualTo("DOCUMENT_FILE_TYPE_REJECTED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Integer.class)).isZero();
        assertThat(storage.size()).isZero();
    }

    @Test
    void softDeletedOrGeometrylessOrUnknownPropertyIs404ForListAndUpload() throws Exception {
        UUID deleted = insertProperty(true, true);
        UUID noGeometry = insertProperty(false, false);

        for (UUID id : List.of(deleted, noGeometry, UUID.randomUUID())) {
            JsonNode list = json(mockMvc.perform(get("/api/v1/properties/{id}/documents", id))
                    .andExpect(status().isNotFound()));
            assertThat(list.get("message").asText()).isEqualTo("PROPERTY_NOT_FOUND");

            JsonNode up = json(upload(id, "titre_foncier", "a.pdf").andExpect(status().isNotFound()));
            assertThat(up.get("message").asText()).isEqualTo("PROPERTY_NOT_FOUND");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Integer.class)).isZero();
        assertThat(storage.size()).isZero();
    }
}
