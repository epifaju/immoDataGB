package com.immodata.gb.api.imports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.imports.ImportJobStatus;
import com.immodata.gb.application.imports.ImportReport;
import com.immodata.gb.application.imports.ImportRowErrorItem;
import com.immodata.gb.application.imports.ImportService;
import com.immodata.gb.application.imports.ImportStartResult;
import com.immodata.gb.infrastructure.config.ImportProperties;
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

@WebMvcTest(controllers = ImportController.class)
@Import(ApiExceptionHandler.class)
class ImportControllerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0003-000000000001");
    private static final String IMPORTS_URL = "/api/v1/imports";
    private static final String STATUS_URL = "/api/v1/imports/{id}/status";
    private static final String REPORT_URL = "/api/v1/imports/{id}/report";
    private static final Instant CREATED_AT = Instant.parse("2026-11-14T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImportService service;

    @MockBean
    private ImportProperties properties;

    private void enabled(boolean value) {
        when(properties.mutationsEnabled()).thenReturn(value);
    }

    private static MockMultipartFile csv() {
        return new MockMultipartFile("file", "import.csv", "text/csv", "a,b\n1,2\n".getBytes());
    }

    private static ImportJobStatus jobStatus() {
        return new ImportJobStatus(JOB_ID, "completed", "import.csv", 3, 2, 1, null, CREATED_AT);
    }

    @Test
    void startReturns202WithJobIdAndQueuedStatus() throws Exception {
        enabled(true);
        when(service.start(any())).thenReturn(new ImportStartResult(JOB_ID, "queued"));

        mockMvc.perform(multipart(IMPORTS_URL).file(csv()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("queued"));
        verify(service).start(any());
    }

    @Test
    void startReturns404WhenMutationsDisabled() throws Exception {
        enabled(false);

        mockMvc.perform(multipart(IMPORTS_URL).file(csv()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(service);
    }

    @Test
    void startWithoutFileReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(multipart(IMPORTS_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("IMPORT_FILE_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void startWithEmptyFileReturns400() throws Exception {
        enabled(true);
        MockMultipartFile empty = new MockMultipartFile("file", "import.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart(IMPORTS_URL).file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("IMPORT_FILE_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void startServiceErrorsAreMapped() throws Exception {
        enabled(true);
        when(service.start(any())).thenThrow(new ApiException("IMPORT_INVALID_TYPE", HttpStatus.BAD_REQUEST));

        mockMvc.perform(multipart(IMPORTS_URL).file(csv()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("IMPORT_INVALID_TYPE"));
    }

    @Test
    void statusReturns200() throws Exception {
        enabled(false);
        when(service.getStatus(JOB_ID)).thenReturn(jobStatus());

        mockMvc.perform(get(STATUS_URL, JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.successRows").value(2))
                .andExpect(jsonPath("$.data.errorRows").value(1))
                .andExpect(jsonPath("$.data.filePath").doesNotExist());
    }

    @Test
    void statusUnknownJobReturns404() throws Exception {
        when(service.getStatus(JOB_ID)).thenThrow(new ApiException("IMPORT_JOB_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(get(STATUS_URL, JOB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("IMPORT_JOB_NOT_FOUND"));
    }

    @Test
    void statusInvalidIdReturns400() throws Exception {
        mockMvc.perform(get(STATUS_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("IMPORT_JOB_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void reportReturns200WithRowErrors() throws Exception {
        ImportReport report = ImportReport.of(
                jobStatus(), List.of(new ImportRowErrorItem(3, "IMPORT_ROW_INVALID", "invalid price", "a,b")));
        when(service.getReport(JOB_ID)).thenReturn(report);

        mockMvc.perform(get(REPORT_URL, JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.errors[0].rowNumber").value(3))
                .andExpect(jsonPath("$.data.errors[0].code").value("IMPORT_ROW_INVALID"));
    }

    @Test
    void reportUnknownJobReturns404() throws Exception {
        when(service.getReport(JOB_ID)).thenThrow(new ApiException("IMPORT_JOB_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(get(REPORT_URL, JOB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("IMPORT_JOB_NOT_FOUND"));
    }

    @Test
    void reportInvalidIdReturns400() throws Exception {
        mockMvc.perform(get(REPORT_URL, "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("IMPORT_JOB_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void listReturnsPageWithDefaults() throws Exception {
        when(service.list(0, 20)).thenReturn(PageResponse.of(0, 20, 1, List.of(jobStatus())));

        mockMvc.perform(get(IMPORTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].jobId").value(JOB_ID.toString()));
    }

    @Test
    void listPassesPageAndSize() throws Exception {
        when(service.list(2, 5)).thenReturn(PageResponse.of(2, 5, 11, List.of(jobStatus())));

        mockMvc.perform(get(IMPORTS_URL).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));
        verify(service).list(2, 5);
    }

    @Test
    void listNonNumericPageReturns400() throws Exception {
        mockMvc.perform(get(IMPORTS_URL).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("IMPORT_PAGE_INVALID"));
        verifyNoInteractions(service);
    }
}
