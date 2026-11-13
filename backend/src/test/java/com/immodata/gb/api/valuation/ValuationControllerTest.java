package com.immodata.gb.api.valuation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.application.valuation.ComparableItem;
import com.immodata.gb.application.valuation.EstimateRequest;
import com.immodata.gb.application.valuation.EstimateResult;
import com.immodata.gb.application.valuation.ValuationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

@WebMvcTest(controllers = ValuationController.class)
@Import(ApiExceptionHandler.class)
class ValuationControllerTest {

    private static final String URL = "/api/v1/valuations/estimate";
    private static final String VALID_BODY = """
            {"propertyType":"apartment","areaSqm":80,"latitude":11.86,"longitude":-15.6,
             "radiusMeters":2000,"periodMonths":24}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValuationService valuationService;

    private static EstimateResult result(String warning) {
        return new EstimateResult(
                "XOF",
                40_000_000L,
                32_000_000L,
                48_000_000L,
                new BigDecimal("500000.0000"),
                new BigDecimal("400000.0000"),
                new BigDecimal("600000.0000"),
                6,
                7,
                72,
                "high",
                warning,
                List.of(new ComparableItem(
                        UUID.fromString("00000000-0000-0000-0001-000000000001"),
                        "apartment",
                        40_000_000L,
                        new BigDecimal("80.00"),
                        new BigDecimal("500000.0000"),
                        LocalDate.of(2026, 5, 1),
                        350.5)));
    }

    @Test
    void estimateReturnsRangeAndCurrency() throws Exception {
        when(valuationService.estimate(any())).thenReturn(result(null));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.currency").value("XOF"))
                .andExpect(jsonPath("$.data.medianTotal").value(40_000_000))
                .andExpect(jsonPath("$.data.rangeLow").value(32_000_000))
                .andExpect(jsonPath("$.data.rangeHigh").value(48_000_000))
                .andExpect(jsonPath("$.data.comparablesCount").value(6))
                .andExpect(jsonPath("$.data.confidenceLevel").value("high"))
                .andExpect(jsonPath("$.data.comparables[0].distanceMeters").value(350.5));

        ArgumentCaptor<EstimateRequest> captor = ArgumentCaptor.forClass(EstimateRequest.class);
        verify(valuationService).estimate(captor.capture());
        EstimateRequest req = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("apartment", req.propertyType());
        org.junit.jupiter.api.Assertions.assertEquals(2000, req.radiusMeters());
        org.junit.jupiter.api.Assertions.assertEquals(24, req.periodMonths());
    }

    @Test
    void estimateExposesWarningWhenFewComparables() throws Exception {
        when(valuationService.estimate(any())).thenReturn(result("VALUATION_FEW_COMPARABLES"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warning").value("VALUATION_FEW_COMPARABLES"))
                .andExpect(jsonPath("$.data.rangeLow").exists())
                .andExpect(jsonPath("$.data.rangeHigh").exists());
    }

    @Test
    void invalidBoundsReturn400WithoutCallingService() throws Exception {
        String body = """
                {"propertyType":"apartment","areaSqm":-1,"latitude":11.86,"longitude":-15.6,
                 "radiusMeters":2000,"periodMonths":24}
                """;

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("VALUATION_REQUEST_INVALID"));
        verifyNoInteractions(valuationService);
    }

    @Test
    void missingFieldReturns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("VALUATION_REQUEST_INVALID"));
        verifyNoInteractions(valuationService);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("VALUATION_REQUEST_INVALID"));
        verifyNoInteractions(valuationService);
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("VALUATION_REQUEST_INVALID"));
        verifyNoInteractions(valuationService);
    }

    @Test
    void noComparablesReturns404() throws Exception {
        when(valuationService.estimate(any()))
                .thenThrow(new ApiException("VALUATION_NO_COMPARABLES", HttpStatus.NOT_FOUND));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("VALUATION_NO_COMPARABLES"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getReturns405() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("METHOD_NOT_ALLOWED"));
    }
}
