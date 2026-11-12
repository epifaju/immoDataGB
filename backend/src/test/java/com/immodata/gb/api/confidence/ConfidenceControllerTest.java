package com.immodata.gb.api.confidence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.confidence.AppliedRule;
import com.immodata.gb.application.confidence.ConfidenceBatchResult;
import com.immodata.gb.application.confidence.ConfidenceEngineService;
import com.immodata.gb.application.confidence.ConfidenceResult;
import com.immodata.gb.application.confidence.ConfidenceRuleInvalidException;
import com.immodata.gb.infrastructure.config.ConfidenceProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ConfidenceController.class)
@Import(ApiExceptionHandler.class)
class ConfidenceControllerTest {

    private static final UUID PROPERTY_ID = UUID.fromString("00000000-0000-0000-0001-000000000003");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final String PROPERTY_URL = "/api/v1/properties/{id}/confidence/recalculate";
    private static final String ORG_URL = "/api/v1/organisations/{orgId}/confidence/recalculate";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfidenceEngineService engineService;

    @MockBean
    private ConfidenceProperties properties;

    private void enabled(boolean value) {
        when(properties.recalculationEnabled()).thenReturn(value);
    }

    @Test
    void propertyRecalculateReturnsResult() throws Exception {
        enabled(true);
        ConfidenceResult result = new ConfidenceResult(
                PROPERTY_ID,
                80,
                80,
                "high",
                "ok",
                List.of(new AppliedRule("Base", "base", 50)));
        when(engineService.recalculateProperty(PROPERTY_ID)).thenReturn(result);

        mockMvc.perform(post(PROPERTY_URL, PROPERTY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.propertyId").value(PROPERTY_ID.toString()))
                .andExpect(jsonPath("$.data.rawScore").value(80))
                .andExpect(jsonPath("$.data.confidenceScore").value(80))
                .andExpect(jsonPath("$.data.confidenceLevel").value("high"))
                .andExpect(jsonPath("$.data.confidenceSummary").value("ok"))
                .andExpect(jsonPath("$.data.appliedRules[0].ruleName").value("Base"))
                .andExpect(jsonPath("$.data.appliedRules[0].ruleType").value("base"))
                .andExpect(jsonPath("$.data.appliedRules[0].scoreDelta").value(50));
    }

    @Test
    void organisationRecalculateReturnsBatch() throws Exception {
        enabled(true);
        when(engineService.recalculateOrganisation(ORG_ID)).thenReturn(new ConfidenceBatchResult(ORG_ID, 24));

        mockMvc.perform(post(ORG_URL, ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.organisationId").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.data.recalculatedCount").value(24));
    }

    @Test
    void organisationWithoutActivePropertiesReturnsZero() throws Exception {
        enabled(true);
        when(engineService.recalculateOrganisation(ORG_ID)).thenReturn(new ConfidenceBatchResult(ORG_ID, 0));

        mockMvc.perform(post(ORG_URL, ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recalculatedCount").value(0));
    }

    @Test
    void flagOffPropertyReturns404WithoutCallingService() throws Exception {
        enabled(false);

        mockMvc.perform(post(PROPERTY_URL, PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(engineService);
    }

    @Test
    void flagOffOrganisationReturns404WithoutCallingService() throws Exception {
        enabled(false);

        mockMvc.perform(post(ORG_URL, ORG_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(engineService);
    }

    @Test
    void flagOffTakesPrecedenceOverInvalidId() throws Exception {
        enabled(false);

        mockMvc.perform(post(PROPERTY_URL, "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        mockMvc.perform(post(ORG_URL, "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(engineService);
    }

    @Test
    void invalidPropertyIdReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(PROPERTY_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PROPERTY_ID_INVALID"));
        verifyNoInteractions(engineService);
    }

    @Test
    void invalidOrganisationIdReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(ORG_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ORGANISATION_ID_INVALID"));
        verifyNoInteractions(engineService);
    }

    @Test
    void propertyNotFoundReturns404() throws Exception {
        enabled(true);
        when(engineService.recalculateProperty(any()))
                .thenThrow(new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(post(PROPERTY_URL, PROPERTY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));
        verify(engineService).recalculateProperty(PROPERTY_ID);
    }

    @Test
    void organisationNotFoundReturns404() throws Exception {
        enabled(true);
        when(engineService.recalculateOrganisation(any()))
                .thenThrow(new ApiException("ORGANISATION_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(post(ORG_URL, ORG_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ORGANISATION_NOT_FOUND"));
    }

    @Test
    void getOnPropertyPathReturns405() throws Exception {
        enabled(true);

        mockMvc.perform(get(PROPERTY_URL, PROPERTY_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void getOnOrganisationPathReturns405() throws Exception {
        enabled(true);

        mockMvc.perform(get(ORG_URL, ORG_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void invalidRuleReturns500WithNullData() throws Exception {
        enabled(true);
        when(engineService.recalculateProperty(any()))
                .thenThrow(new ConfidenceRuleInvalidException(UUID.randomUUID(), "Règle X", new IllegalStateException("bad")));

        mockMvc.perform(post(PROPERTY_URL, PROPERTY_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void dataAccessReturns503() throws Exception {
        enabled(true);
        when(engineService.recalculateOrganisation(any())).thenThrow(new DataAccessResourceFailureException("down"));

        mockMvc.perform(post(ORG_URL, ORG_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("HEALTH_DATABASE_UNAVAILABLE"));
    }
}
