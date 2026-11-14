package com.immodata.gb.api.admin;

import static org.hamcrest.Matchers.closeTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.admin.AdminValidationService;
import com.immodata.gb.application.admin.RejectResult;
import com.immodata.gb.application.admin.ValidationQueueItem;
import com.immodata.gb.infrastructure.config.AdminProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminValidationController.class)
@Import(ApiExceptionHandler.class)
class AdminValidationControllerTest {

    private static final String QUEUE_URL = "/api/v1/admin/validation-queue";
    private static final String VALIDATE_URL = "/api/v1/admin/properties/{id}/validate";
    private static final String REJECT_URL = "/api/v1/admin/properties/{id}/reject";
    private static final UUID PROPERTY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant CREATED_AT = Instant.parse("2026-11-14T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminValidationService service;

    @MockBean
    private AdminProperties properties;

    private void enabled(boolean value) {
        when(properties.mutationsEnabled()).thenReturn(value);
    }

    private static ValidationQueueItem item() {
        return new ValidationQueueItem(
                PROPERTY_ID, "FLD-20261114-abcd1234", "Casa azul", "house", CREATED_AT, 11.86, -15.6);
    }

    @Test
    void queueReturnsPageWithDefaults() throws Exception {
        when(service.listQueue(0, 20)).thenReturn(PageResponse.of(0, 20, 1, List.of(item())));

        mockMvc.perform(get(QUEUE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(PROPERTY_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].reference").value("FLD-20261114-abcd1234"))
                .andExpect(jsonPath("$.data.items[0].title").value("Casa azul"))
                .andExpect(jsonPath("$.data.items[0].propertyType").value("house"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-14T10:15:30Z"))
                .andExpect(jsonPath("$.data.items[0].latitude").value(closeTo(11.86, 0.000001)))
                .andExpect(jsonPath("$.data.items[0].longitude").value(closeTo(-15.6, 0.000001)));
        verify(service).listQueue(0, 20);
    }

    @Test
    void queuePassesPageAndSize() throws Exception {
        when(service.listQueue(2, 5)).thenReturn(PageResponse.of(2, 5, 11, List.of(item())));

        mockMvc.perform(get(QUEUE_URL).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));
        verify(service).listQueue(2, 5);
    }

    @Test
    void queueTrimsPageAndSize() throws Exception {
        when(service.listQueue(2, 5)).thenReturn(PageResponse.of(2, 5, 0, List.of()));

        mockMvc.perform(get(QUEUE_URL).param("page", " 2 ").param("size", " 5 "))
                .andExpect(status().isOk());
        verify(service).listQueue(2, 5);
    }

    @Test
    void queueNonNumericPageReturns400() throws Exception {
        mockMvc.perform(get(QUEUE_URL).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("ADMIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void queueNonNumericSizeReturns400() throws Exception {
        mockMvc.perform(get(QUEUE_URL).param("size", "20x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ADMIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void queueStaysOpenWhenMutationsDisabled() throws Exception {
        enabled(false);
        when(service.listQueue(0, 20)).thenReturn(PageResponse.of(0, 20, 0, List.of()));

        mockMvc.perform(get(QUEUE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).listQueue(0, 20);
    }

    @Test
    void validateReturns200() throws Exception {
        enabled(true);
        when(service.validate(PROPERTY_ID)).thenReturn(item());

        mockMvc.perform(post(VALIDATE_URL, PROPERTY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROPERTY_ID.toString()))
                .andExpect(jsonPath("$.data.reference").value("FLD-20261114-abcd1234"))
                .andExpect(jsonPath("$.data.propertyType").value("house"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-14T10:15:30Z"));
        verify(service).validate(PROPERTY_ID);
    }

    @Test
    void validateReturns404WhenMutationsDisabled() throws Exception {
        enabled(false);

        mockMvc.perform(post(VALIDATE_URL, PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(service);
    }

    @Test
    void validateInvalidIdWhenDisabledStillReturns404() throws Exception {
        enabled(false);

        mockMvc.perform(post(VALIDATE_URL, "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(service);
    }

    @Test
    void validateInvalidIdReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(VALIDATE_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("PROPERTY_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void validateMissingPropertyReturns404() throws Exception {
        enabled(true);
        when(service.validate(PROPERTY_ID))
                .thenThrow(new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(post(VALIDATE_URL, PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));
    }

    @Test
    void rejectReturns200() throws Exception {
        enabled(true);
        when(service.reject(PROPERTY_ID)).thenReturn(new RejectResult(PROPERTY_ID, true));

        mockMvc.perform(post(REJECT_URL, PROPERTY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROPERTY_ID.toString()))
                .andExpect(jsonPath("$.data.deleted").value(true));
        verify(service).reject(PROPERTY_ID);
    }

    @Test
    void rejectReturns404WhenMutationsDisabled() throws Exception {
        enabled(false);

        mockMvc.perform(post(REJECT_URL, PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectInvalidIdReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(REJECT_URL, "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PROPERTY_ID_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectMissingPropertyReturns404() throws Exception {
        enabled(true);
        when(service.reject(PROPERTY_ID))
                .thenThrow(new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(post(REJECT_URL, PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));
    }
}
