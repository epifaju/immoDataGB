package com.immodata.gb.api.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.admin.AdminValidationService;
import com.immodata.gb.application.admin.AuditEventView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminAuditController.class)
@Import(ApiExceptionHandler.class)
class AdminAuditControllerTest {

    private static final String AUDIT_URL = "/api/v1/admin/audit";
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROPERTY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant CREATED_AT = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-14T23:59:59Z");
    private static final String OLD_VALUE = "{\"validationStatus\":\"pending_validation\"}";
    private static final String NEW_VALUE = "{\"validationStatus\":\"validated\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminValidationService service;

    private static AuditEventView event() {
        return new AuditEventView(
                EVENT_ID,
                "property",
                PROPERTY_ID,
                "validate",
                OLD_VALUE,
                NEW_VALUE,
                "Bem validado (FLD-20261114-abcd1234).",
                CREATED_AT);
    }

    @Test
    void listReturnsPageWithDefaults() throws Exception {
        when(service.listAudit(null, null, null, null, 0, 20))
                .thenReturn(PageResponse.of(0, 20, 1, List.of(event())));

        mockMvc.perform(get(AUDIT_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].entityType").value("property"))
                .andExpect(jsonPath("$.data.items[0].entityId").value(PROPERTY_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].action").value("validate"))
                .andExpect(jsonPath("$.data.items[0].oldValue").value(OLD_VALUE))
                .andExpect(jsonPath("$.data.items[0].newValue").value(NEW_VALUE))
                .andExpect(jsonPath("$.data.items[0].summary").value("Bem validado (FLD-20261114-abcd1234)."))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-14T12:00:00Z"));
        verify(service).listAudit(null, null, null, null, 0, 20);
    }

    @Test
    void listPassesFilters() throws Exception {
        when(service.listAudit("property", "validate", FROM, TO, 1, 10))
                .thenReturn(PageResponse.of(1, 10, 11, List.of(event())));

        mockMvc.perform(get(AUDIT_URL)
                        .param("entityType", "property")
                        .param("action", "validate")
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-14T23:59:59Z")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        verify(service).listAudit("property", "validate", FROM, TO, 1, 10);
    }

    @Test
    void listTrimsInstantsAndPage() throws Exception {
        when(service.listAudit("property", "reject", FROM, TO, 3, 20))
                .thenReturn(PageResponse.of(3, 20, 0, List.of()));

        mockMvc.perform(get(AUDIT_URL)
                        .param("entityType", "property")
                        .param("action", "reject")
                        .param("from", " 2026-11-01T00:00:00Z ")
                        .param("to", " 2026-11-14T23:59:59Z ")
                        .param("page", " 3 "))
                .andExpect(status().isOk());
        verify(service).listAudit("property", "reject", FROM, TO, 3, 20);
    }

    @Test
    void listAcceptsOffsetInstant() throws Exception {
        Instant from = Instant.parse("2026-11-14T09:00:00Z");
        when(service.listAudit(null, null, from, null, 0, 20))
                .thenReturn(PageResponse.of(0, 20, 0, List.of()));

        mockMvc.perform(get(AUDIT_URL).param("from", "2026-11-14T10:00:00+01:00"))
                .andExpect(status().isOk());
        verify(service).listAudit(null, null, from, null, 0, 20);
    }

    @Test
    void listBadFromReturns400() throws Exception {
        mockMvc.perform(get(AUDIT_URL).param("from", "2026-11-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("ADMIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void listBadToReturns400() throws Exception {
        mockMvc.perform(get(AUDIT_URL).param("to", "not-an-instant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ADMIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void listBlankFromReturns400() throws Exception {
        mockMvc.perform(get(AUDIT_URL).param("from", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ADMIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void listNonNumericPageReturns400() throws Exception {
        mockMvc.perform(get(AUDIT_URL).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ADMIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void listNonNumericSizeReturns400() throws Exception {
        mockMvc.perform(get(AUDIT_URL).param("size", "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ADMIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }
}
