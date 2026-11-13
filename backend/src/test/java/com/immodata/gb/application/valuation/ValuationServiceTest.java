package com.immodata.gb.application.valuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.property.ConfidenceLevel;
import com.immodata.gb.domain.valuation.ComparableFact;
import com.immodata.gb.domain.valuation.ValuationCalculator;
import com.immodata.gb.domain.valuation.ValuationComputation;
import com.immodata.gb.infrastructure.persistence.valuation.ValuationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ValuationServiceTest {

    private final ValuationRepository repository = mock(ValuationRepository.class);
    private final ValuationCalculator calculator = mock(ValuationCalculator.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
    private final ValuationService service = new ValuationService(repository, calculator, clock);

    private final EstimateRequest request =
            new EstimateRequest("apartment", new BigDecimal("80"), 11.863, -15.598, 5000, 24);

    private static ComparableFact fact(long price, String area) {
        return new ComparableFact(
                UUID.randomUUID(), "apartment", price, new BigDecimal(area), LocalDate.of(2026, 6, 1), 420.5);
    }

    @Test
    void throwsNoComparablesWhenRepositoryEmpty() {
        when(repository.findCandidates(anyString(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.estimate(request));

        assertEquals("VALUATION_NO_COMPARABLES", ex.getCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(calculator, never()).compute(any(), any());
    }

    @Test
    void usesClockToComputeSinceDate() {
        when(repository.findCandidates(anyString(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of());

        assertThrows(ApiException.class, () -> service.estimate(request));

        verify(repository).findCandidates("apartment", 11.863, -15.598, 5000, LocalDate.of(2024, 11, 13));
    }

    @Test
    void mapsComputationToResult() {
        ComparableFact kept = fact(11_000_000, "80.00");
        ComparableFact outlier = fact(90_000_000, "80.00");
        List<ComparableFact> candidates = List.of(kept, outlier);
        when(repository.findCandidates(eq("apartment"), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(candidates);
        ValuationComputation computation = new ValuationComputation(
                10_000_000,
                12_000_000,
                14_000_000,
                new BigDecimal("125000.0000"),
                new BigDecimal("150000.0000"),
                new BigDecimal("175000.0000"),
                1,
                1,
                15,
                ConfidenceLevel.LOW,
                "Amostra insuficiente (n=1). A estimativa é indicativa; recomenda-se prudência.",
                List.of(kept));
        when(calculator.compute(candidates, request.areaSqm())).thenReturn(computation);

        EstimateResult r = service.estimate(request);

        assertEquals("XOF", r.currency());
        assertEquals(12_000_000, r.medianTotal());
        assertEquals(10_000_000, r.rangeLow());
        assertEquals(14_000_000, r.rangeHigh());
        assertEquals(new BigDecimal("150000.0000"), r.medianPricePerSqm());
        assertEquals(new BigDecimal("125000.0000"), r.rangeLowPricePerSqm());
        assertEquals(new BigDecimal("175000.0000"), r.rangeHighPricePerSqm());
        assertEquals(1, r.comparablesCount());
        assertEquals(2, r.comparablesRawCount());
        assertEquals(15, r.confidenceScore());
        assertEquals("low", r.confidenceLevel());
        assertNotNull(r.warning());
        assertEquals(1, r.comparables().size());
        ComparableItem item = r.comparables().getFirst();
        assertEquals(kept.id(), item.id());
        assertEquals(11_000_000, item.priceXof());
        assertEquals(new BigDecimal("137500.0000"), item.pricePerSqm());
        assertEquals(420.5, item.distanceMeters());
    }

    @Test
    void keepsNullWarningWhenComputationHasNone() {
        ComparableFact f = fact(10_000_000, "100.00");
        when(repository.findCandidates(anyString(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(f));
        when(calculator.compute(any(), any()))
                .thenReturn(new ValuationComputation(
                        1,
                        2,
                        3,
                        BigDecimal.ONE,
                        BigDecimal.TWO,
                        BigDecimal.TEN,
                        5,
                        0,
                        75,
                        ConfidenceLevel.HIGH,
                        null,
                        List.of(f)));

        EstimateResult r = service.estimate(request);

        assertNull(r.warning());
        assertEquals("high", r.confidenceLevel());
    }
}