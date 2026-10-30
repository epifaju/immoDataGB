package com.immodata.gb.api.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.infrastructure.health.PostgisHealthProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import(ApiExceptionHandler.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostgisHealthProbe probe;

    @Test
    void databaseFailureReturns503() throws Exception {
        when(probe.probe()).thenThrow(new DataAccessResourceFailureException("down"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("HEALTH_DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.api").value("up"))
                .andExpect(jsonPath("$.data.database").value("down"))
                .andExpect(jsonPath("$.data.postgis").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meta").isMap());
    }

    @Test
    void unexpectedFailureReturns500() throws Exception {
        when(probe.probe()).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meta").isMap());
    }
}
