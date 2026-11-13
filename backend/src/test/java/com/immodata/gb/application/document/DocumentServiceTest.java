package com.immodata.gb.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.config.DocumentProperties;
import com.immodata.gb.infrastructure.persistence.document.DocumentRepository;
import com.immodata.gb.infrastructure.persistence.document.DocumentRepository.DocumentRow;
import com.immodata.gb.infrastructure.storage.DocumentStorage;
import com.immodata.gb.infrastructure.storage.StorageKeyFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

class DocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String BUCKET = "documents";

    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final DocumentFileValidator validator = mock(DocumentFileValidator.class);
    private final StorageKeyFactory keyFactory = new StorageKeyFactory();
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final MultipartFile multipart = mock(MultipartFile.class);

    private DocumentService service;

    private final UUID propertyId = UUID.randomUUID();
    private final UUID organisationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DocumentProperties properties = new DocumentProperties(true, BUCKET, TTL);
        service = new DocumentService(repository, validator, keyFactory, storage, properties, clock);
    }

    private ValidatedFile validated() throws Exception {
        when(multipart.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(new byte[] {1, 2, 3}));
        return new ValidatedFile("Titre foncier.pdf", "pdf", "application/pdf", 3L, multipart);
    }

    @Test
    void listThrowsPropertyNotFoundWhenPropertyMissing() {
        when(repository.propertyExistsActiveWithGeometry(propertyId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(propertyId))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("PROPERTY_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verify(repository, never()).listActiveByProperty(any());
    }

    @Test
    void listReturnsEmptyListWhenNoDocument() {
        when(repository.propertyExistsActiveWithGeometry(propertyId)).thenReturn(true);
        when(repository.listActiveByProperty(propertyId)).thenReturn(List.of());

        assertThat(service.list(propertyId)).isEmpty();
    }

    @Test
    void listReturnsDocuments() {
        DocumentItem item =
                new DocumentItem(UUID.randomUUID(), propertyId, "autre", "a.pdf", "application/pdf", 3L, NOW);
        when(repository.propertyExistsActiveWithGeometry(propertyId)).thenReturn(true);
        when(repository.listActiveByProperty(propertyId)).thenReturn(List.of(item));

        assertThat(service.list(propertyId)).containsExactly(item);
    }

    @Test
    void uploadInsertsThenPutsAndReturnsItem() throws Exception {
        ValidatedFile file = validated();
        when(validator.validate(multipart)).thenReturn(file);
        when(repository.findOrganisationIdForActiveProperty(propertyId)).thenReturn(Optional.of(organisationId));

        DocumentItem result = service.upload(propertyId, "titre_foncier", multipart);

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(repository, storage);
        order.verify(repository)
                .insert(
                        idCaptor.capture(),
                        eq(organisationId),
                        eq(propertyId),
                        eq("titre_foncier"),
                        eq("Titre foncier.pdf"),
                        eq("application/pdf"),
                        eq(3L),
                        eq(BUCKET),
                        keyCaptor.capture(),
                        isNull());
        order.verify(storage).put(eq(BUCKET), eq(keyCaptor.getValue()), any(), eq(3L), eq("application/pdf"));

        UUID documentId = idCaptor.getValue();
        assertThat(keyCaptor.getValue())
                .isEqualTo(organisationId + "/" + propertyId + "/" + documentId + "/Titre_foncier.pdf");
        assertThat(result.id()).isEqualTo(documentId);
        assertThat(result.propertyId()).isEqualTo(propertyId);
        assertThat(result.documentType()).isEqualTo("titre_foncier");
        assertThat(result.originalFilename()).isEqualTo("Titre foncier.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.sizeBytes()).isEqualTo(3L);
        assertThat(result.createdAt()).isEqualTo(NOW);
    }

    @Test
    void uploadRejectsInvalidTypeBeforeAnyWork() {
        assertThatThrownBy(() -> service.upload(propertyId, "TITRE_FONCIER", multipart))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("DOCUMENT_TYPE_INVALID");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verifyNoInteractions(validator, storage);
        verify(repository, never()).insert(any(), any(), any(), any(), any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void uploadPropagatesValidatorRejection() {
        when(validator.validate(multipart)).thenThrow(new ApiException("DOCUMENT_FILE_TOO_LARGE", HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.upload(propertyId, "autre", multipart))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DOCUMENT_FILE_TOO_LARGE"));
        verifyNoInteractions(storage);
    }

    @Test
    void uploadThrowsPropertyNotFoundWhenNoOrganisation() throws Exception {
        ValidatedFile file = validated();
        when(validator.validate(multipart)).thenReturn(file);
        when(repository.findOrganisationIdForActiveProperty(propertyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(propertyId, "autre", multipart))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("PROPERTY_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verifyNoInteractions(storage);
    }

    @Test
    void uploadMapsStorageFailureTo503() throws Exception {
        ValidatedFile file = validated();
        when(validator.validate(multipart)).thenReturn(file);
        when(repository.findOrganisationIdForActiveProperty(propertyId)).thenReturn(Optional.of(organisationId));
        doThrow(new RuntimeException("s3 down")).when(storage).put(anyString(), anyString(), any(), anyLong(), anyString());

        assertThatThrownBy(() -> service.upload(propertyId, "autre", multipart))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("DOCUMENT_STORAGE_UNAVAILABLE");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void uploadMapsStreamOpenFailureTo503() throws Exception {
        when(multipart.getInputStream()).thenThrow(new java.io.IOException("boom"));
        when(validator.validate(multipart))
                .thenReturn(new ValidatedFile("a.pdf", "pdf", "application/pdf", 3L, multipart));
        when(repository.findOrganisationIdForActiveProperty(propertyId)).thenReturn(Optional.of(organisationId));

        assertThatThrownBy(() -> service.upload(propertyId, "autre", multipart))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DOCUMENT_STORAGE_UNAVAILABLE"));
    }

    @Test
    void signedUrlReturnsUrlAndExpiry() {
        UUID documentId = UUID.randomUUID();
        DocumentRow row = new DocumentRow(
                documentId, organisationId, propertyId, "autre", "a.pdf", "application/pdf", 3L, "bkt", "k/a.pdf", NOW);
        when(repository.findActiveById(documentId)).thenReturn(Optional.of(row));
        when(storage.presignGet("bkt", "k/a.pdf", TTL)).thenReturn(URI.create("http://minio/signed?x=1"));

        SignedUrlResult result = service.signedUrl(documentId);

        assertThat(result.url()).isEqualTo("http://minio/signed?x=1");
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void signedUrlThrowsNotFound() {
        UUID documentId = UUID.randomUUID();
        when(repository.findActiveById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signedUrl(documentId))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("DOCUMENT_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verifyNoInteractions(storage);
    }

    @Test
    void signedUrlMapsPresignFailureTo503() {
        UUID documentId = UUID.randomUUID();
        DocumentRow row = new DocumentRow(
                documentId, organisationId, propertyId, "autre", "a.pdf", "application/pdf", 3L, "bkt", "k/a.pdf", NOW);
        when(repository.findActiveById(documentId)).thenReturn(Optional.of(row));
        when(storage.presignGet(anyString(), anyString(), any())).thenThrow(new RuntimeException("s3 down"));

        assertThatThrownBy(() -> service.signedUrl(documentId))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("DOCUMENT_STORAGE_UNAVAILABLE");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void softDeleteSucceedsOnceThenNotFound() {
        UUID documentId = UUID.randomUUID();
        when(repository.softDelete(documentId)).thenReturn(true, false);

        service.softDelete(documentId);

        assertThatThrownBy(() -> service.softDelete(documentId))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DOCUMENT_NOT_FOUND"));
    }
}
