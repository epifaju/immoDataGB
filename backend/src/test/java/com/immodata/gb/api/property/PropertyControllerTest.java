package com.immodata.gb.api.property;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.property.PropertyDetail;
import com.immodata.gb.application.property.PropertyMapItem;
import com.immodata.gb.application.property.PropertyQueryService;
import com.immodata.gb.application.property.SourceItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PropertyController.class)
@Import(ApiExceptionHandler.class)
class PropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyQueryService propertyQueryService;

    @Test
    void listSuccessEnvelope() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0001-000000000024");
        PropertyMapItem item = new PropertyMapItem(
                id,
                UUID.fromString("00000000-0000-0000-0000-00000000000a"),
                "BIS-024",
                "Espaço comercial",
                "commercial",
                91,
                "high",
                "ok",
                -15.52,
                11.928);
        when(propertyQueryService.list(any())).thenReturn(PageResponse.of(0, 20, 24, List.of(item)));

        mockMvc.perform(get("/api/v1/properties").param("bbox", "-15.70,11.80,-15.50,11.95"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Cache-Control"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].reference").value("BIS-024"))
                .andExpect(jsonPath("$.data.items[0].docStatus").doesNotExist());
    }

    @Test
    void detailReturnsPropertyDetail() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0001-000000000003");
        PropertyDetail detail = new PropertyDetail(
                id,
                UUID.fromString("00000000-0000-0000-0000-00000000000a"),
                "BIS-003",
                "Terreno",
                "land",
                12,
                "low",
                "ok",
                -15.616,
                11.82,
                "partial",
                null);
        when(propertyQueryService.getDetail(id)).thenReturn(Optional.of(detail));

        mockMvc.perform(get("/api/v1/properties/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.docStatus").value("partial"))
                .andExpect(jsonPath("$.data.addressText").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void sourcesReturnWeightWithoutPropertyIdOrVerified() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0001-000000000005");
        SourceItem source = new SourceItem(
                UUID.fromString("00000000-0000-0000-0003-000000000501"),
                "field_observation",
                "Observação de campo 5.1",
                LocalDate.of(2026, 8, 7),
                false,
                2);
        when(propertyQueryService.listSources(id)).thenReturn(Optional.of(List.of(source)));

        mockMvc.perform(get("/api/v1/properties/{id}/sources", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceType").value("field_observation"))
                .andExpect(jsonPath("$.data[0].isVerified").value(false))
                .andExpect(jsonPath("$.data[0].observedAt").value("2026-08-07"))
                .andExpect(jsonPath("$.data[0].weight").value(2))
                .andExpect(jsonPath("$.data[0].propertyId").doesNotExist())
                .andExpect(jsonPath("$.data[0].verified").doesNotExist());
    }

    @Test
    void sourcesEmptyList() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0001-000000000099");
        when(propertyQueryService.listSources(id)).thenReturn(Optional.of(List.of()));

        mockMvc.perform(get("/api/v1/properties/{id}/sources", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidBboxReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PROPERTY_BBOX_INVALID"));
    }

    @Test
    void notFoundOnMissingProperty() throws Exception {
        when(propertyQueryService.getDetail(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/properties/00000000-0000-0000-0001-000000000099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));
    }

    @Test
    void sourcesNotFoundWhenOptionalEmpty() throws Exception {
        when(propertyQueryService.listSources(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/properties/00000000-0000-0000-0001-000000000099/sources"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PROPERTY_NOT_FOUND"));
    }

    @Test
    void invalidIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/properties/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PROPERTY_ID_INVALID"));
    }

    @Test
    void dataAccessReturns503() throws Exception {
        when(propertyQueryService.list(any())).thenThrow(new DataAccessResourceFailureException("down"));

        mockMvc.perform(get("/api/v1/properties").param("bbox", "-15.70,11.80,-15.50,11.95"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("HEALTH_DATABASE_UNAVAILABLE"));
    }

    @Test
    void unexpectedFailureReturns500() throws Exception {
        when(propertyQueryService.list(any())).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/v1/properties").param("bbox", "-15.70,11.80,-15.50,11.95"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("INTERNAL_ERROR"));
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("NOT_FOUND"));
    }

    @Test
    void postMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/properties").param("bbox", "-15.70,11.80,-15.50,11.95"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("METHOD_NOT_ALLOWED"));
    }
}
