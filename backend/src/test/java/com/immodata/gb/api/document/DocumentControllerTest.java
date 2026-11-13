package com.immodata.gb.api.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.document.DocumentItem;
import com.immodata.gb.application.document.DocumentService;
import com.immodata.gb.application.document.SignedUrlResult;
import com.immodata.gb.infrastructure.config.DocumentProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DocumentController.class)
@Import(ApiExceptionHandler.class)
class DocumentControllerTest {

    private static final UUID PROPERTY_ID = UUID.fromString("00000000-0000-0000-0001-000000000003");
    private static final UUID DOCUMENT_ID = UUID.fromString("00000000-0000-0000-0002-000000000001");
    private static final String LIST_URL = "/api/v1/properties/{id}/documents";
    private static final String SIGNED_URL = "/api/v1/documents/{id}/signed-url";
    private static final String DOCUMENT_URL = "/api/v1/documents/{id}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService service;

    @MockBean
    private DocumentProperties properties;

    private void enabled(boolean value) {
        when(properties.mutationsEnabled()).thenReturn(value);
    }

    private static DocumentItem item() {
        return new DocumentItem(
                DOCUMENT_ID,
                PROPERTY_ID,
                "titre_foncier",
                "titre.pdf",
                "application/pdf",
                1024L,
                Instant.parse("2026-11-13T10:00:00Z"));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "titre.pdf", "application/pdf", new byte[] {1, 2, 3});
    }

    @Test
    void listWorksWithFlagOffAndDoesNotExposeStorageKey() throws Exception {
        enabled(false);
        when(service.list(PROPERTY_ID)).thenReturn(List.of(item()));

        mockMvc.perform(get(LIST_URL, PROPERTY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.data[0].propertyId").value(PROPERTY_ID.toString()))
                .andExpect(jsonPath("$.data[0].documentType").value("titre_foncier"))
                .andExpect(jsonPath("$.data[0].originalFilename").value("titre.pdf"))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].storageBucket").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("storage"))));
    }

    @Test
    void listInvalidPropertyIdReturns400() throws Exception {
        mockMvc.perform(get(LIST_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PROPERTY_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void listPropertyNotFoundReturns404() throws Exception {
        when(service.list(any())).thenThrow(new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(get(LIST_URL, PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));
    }

    @Test
    void uploadReturns201() throws Exception {
        enabled(true);
        when(service.upload(eq(PROPERTY_ID), eq("titre_foncier"), any())).thenReturn(item());

        mockMvc.perform(multipart(LIST_URL, PROPERTY_ID).file(file()).param("documentType", "titre_foncier"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
        verify(service).upload(eq(PROPERTY_ID), eq("titre_foncier"), any());
    }

    @Test
    void uploadWithoutFileDelegatesToServiceValidation() throws Exception {
        enabled(true);
        when(service.upload(eq(PROPERTY_ID), eq("titre_foncier"), eq(null)))
                .thenThrow(new ApiException("DOCUMENT_FILE_MISSING", HttpStatus.BAD_REQUEST));

        mockMvc.perform(multipart(LIST_URL, PROPERTY_ID).param("documentType", "titre_foncier"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("DOCUMENT_FILE_MISSING"));
    }

    @Test
    void uploadServiceErrorsAreMapped() throws Exception {
        enabled(true);
        org.mockito.Mockito.reset(service);
        when(service.upload(any(), any(), any()))
                .thenThrow(new ApiException("DOCUMENT_TYPE_INVALID", HttpStatus.BAD_REQUEST));
        mockMvc.perform(multipart(LIST_URL, PROPERTY_ID).file(file()).param("documentType", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("DOCUMENT_TYPE_INVALID"));

        org.mockito.Mockito.reset(service);
        when(service.upload(any(), any(), any()))
                .thenThrow(new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));
        mockMvc.perform(multipart(LIST_URL, PROPERTY_ID).file(file()).param("documentType", "autre"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));

        org.mockito.Mockito.reset(service);
        when(service.upload(any(), any(), any()))
                .thenThrow(new ApiException("DOCUMENT_STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE));
        mockMvc.perform(multipart(LIST_URL, PROPERTY_ID).file(file()).param("documentType", "autre"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("DOCUMENT_STORAGE_UNAVAILABLE"));
    }

    @Test
    void uploadInvalidPropertyIdReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(multipart(LIST_URL, "not-a-uuid").file(file()).param("documentType", "autre"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PROPERTY_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void signedUrlReturns200() throws Exception {
        enabled(true);
        Instant expiresAt = Instant.parse("2026-11-13T10:15:00Z");
        when(service.signedUrl(DOCUMENT_ID)).thenReturn(new SignedUrlResult("https://minio/x?sig=1", expiresAt));

        mockMvc.perform(post(SIGNED_URL, DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://minio/x?sig=1"))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    void signedUrlErrorsAreMapped() throws Exception {
        enabled(true);
        when(service.signedUrl(any())).thenThrow(new ApiException("DOCUMENT_NOT_FOUND", HttpStatus.NOT_FOUND));
        mockMvc.perform(post(SIGNED_URL, DOCUMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("DOCUMENT_NOT_FOUND"));

        org.mockito.Mockito.reset(service);
        when(service.signedUrl(any()))
                .thenThrow(new ApiException("DOCUMENT_STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE));
        mockMvc.perform(post(SIGNED_URL, DOCUMENT_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("DOCUMENT_STORAGE_UNAVAILABLE"));
    }

    @Test
    void signedUrlInvalidIdReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(SIGNED_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("DOCUMENT_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void getOnSignedUrlPathReturns405() throws Exception {
        enabled(true);

        mockMvc.perform(get(SIGNED_URL, DOCUMENT_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void deleteReturns200WithNullData() throws Exception {
        enabled(true);

        mockMvc.perform(delete(DOCUMENT_URL, DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).softDelete(DOCUMENT_ID);
    }

    @Test
    void deleteNotFoundReturns404() throws Exception {
        enabled(true);
        org.mockito.Mockito.doThrow(new ApiException("DOCUMENT_NOT_FOUND", HttpStatus.NOT_FOUND))
                .when(service)
                .softDelete(any());

        mockMvc.perform(delete(DOCUMENT_URL, DOCUMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void deleteInvalidIdReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(delete(DOCUMENT_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("DOCUMENT_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void flagOffReturns404OnAllMutationsEvenWithInvalidIds() throws Exception {
        enabled(false);

        mockMvc.perform(multipart(LIST_URL, "not-a-uuid").file(file()).param("documentType", "autre"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        mockMvc.perform(post(SIGNED_URL, "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        mockMvc.perform(delete(DOCUMENT_URL, "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        mockMvc.perform(multipart(LIST_URL, PROPERTY_ID).file(file()).param("documentType", "autre"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(SIGNED_URL, DOCUMENT_ID)).andExpect(status().isNotFound());
        mockMvc.perform(delete(DOCUMENT_URL, DOCUMENT_ID)).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
