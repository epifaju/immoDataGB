package com.immodata.gb.api.property;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.property.PropertyMapItem;
import com.immodata.gb.application.property.PropertyQueryService;
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
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.totalElements").value(24))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].reference").value("BIS-024"))
                .andExpect(jsonPath("$.meta").isMap());
    }

    @Test
    void invalidBboxReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PROPERTY_BBOX_INVALID"));
    }

    @Test
    void notFoundOnMissingProperty() throws Exception {
        when(propertyQueryService.get(any()))
                .thenThrow(new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/properties/00000000-0000-0000-0001-000000000099"))
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
