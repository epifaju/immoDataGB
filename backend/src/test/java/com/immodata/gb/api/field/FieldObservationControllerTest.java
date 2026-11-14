package com.immodata.gb.api.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.field.FieldObservationCreated;
import com.immodata.gb.application.field.FieldObservationRequest;
import com.immodata.gb.application.field.FieldObservationService;
import com.immodata.gb.infrastructure.config.FieldProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FieldObservationController.class)
@Import(ApiExceptionHandler.class)
class FieldObservationControllerTest {

    private static final String URL = "/api/v1/field-observations";
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0004-000000000001");
    private static final String VALID_BODY = """
            {"latitude":11.86,"longitude":-15.6,"propertyType":"house",
             "title":"Casa Bairro Militar","occupationStatus":"occupied","notes":"Portão azul"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FieldObservationService service;

    @MockBean
    private FieldProperties properties;

    private void enabled(boolean value) {
        when(properties.mutationsEnabled()).thenReturn(value);
    }

    private static FieldObservationCreated created() {
        return new FieldObservationCreated(
                ID, "FLD-20261114-abcd1234", "Casa Bairro Militar", "house", "pending_validation", 11.86, -15.6, 12, "low");
    }

    @Test
    void createReturns201WithPendingObservation() throws Exception {
        enabled(true);
        when(service.create(any())).thenReturn(created());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.reference").value("FLD-20261114-abcd1234"))
                .andExpect(jsonPath("$.data.validationStatus").value("pending_validation"))
                .andExpect(jsonPath("$.data.confidenceScore").value(12))
                .andExpect(jsonPath("$.data.confidenceLevel").value("low"));
    }

    @Test
    void createPassesParsedRequestToService() throws Exception {
        enabled(true);
        when(service.create(any())).thenReturn(created());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<FieldObservationRequest> captor = ArgumentCaptor.forClass(FieldObservationRequest.class);
        verify(service).create(captor.capture());
        FieldObservationRequest req = captor.getValue();
        assertThat(req.latitude()).isEqualTo(11.86);
        assertThat(req.longitude()).isEqualTo(-15.6);
        assertThat(req.propertyType()).isEqualTo("house");
        assertThat(req.title()).isEqualTo("Casa Bairro Militar");
        assertThat(req.occupationStatus()).isEqualTo("occupied");
        assertThat(req.notes()).isEqualTo("Portão azul");
    }

    @Test
    void createReturns404WhenMutationsDisabled() throws Exception {
        enabled(false);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(service);
    }

    @Test
    void createDisabledWithInvalidBodyStillReturns404() throws Exception {
        enabled(false);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
        verifyNoInteractions(service);
    }

    @Test
    void createInvalidBodyReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":95,\"longitude\":-15.6,\"propertyType\":\"house\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("FIELD_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void createMissingPropertyTypeReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":11.86,\"longitude\":-15.6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("FIELD_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void createMalformedJsonReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("FIELD_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void createEmptyBodyReturns400() throws Exception {
        enabled(true);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("FIELD_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void createServiceErrorsAreMapped() throws Exception {
        enabled(true);
        when(service.create(any()))
                .thenThrow(new ApiException("FIELD_ORGANISATION_MISSING", HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("FIELD_ORGANISATION_MISSING"));
    }
}
