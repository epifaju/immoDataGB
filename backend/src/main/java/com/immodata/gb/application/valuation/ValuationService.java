package com.immodata.gb.application.valuation;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.valuation.ComparableFact;
import com.immodata.gb.domain.valuation.ValuationCalculator;
import com.immodata.gb.domain.valuation.ValuationComputation;
import com.immodata.gb.infrastructure.persistence.valuation.ValuationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValuationService {

    static final String CURRENCY = "XOF";
    private static final int PPS_SCALE = 4;

    private final ValuationRepository repository;
    private final ValuationCalculator calculator;
    private final Clock clock;

    public ValuationService(ValuationRepository repository, ValuationCalculator calculator, Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EstimateResult estimate(EstimateRequest req) {
        LocalDate since = LocalDate.now(clock).minusMonths(req.periodMonths());
        List<ComparableFact> candidates = repository.findCandidates(
                req.propertyType(), req.latitude(), req.longitude(), req.radiusMeters(), since);
        if (candidates.isEmpty()) {
            throw new ApiException("VALUATION_NO_COMPARABLES", HttpStatus.NOT_FOUND);
        }
        ValuationComputation c = calculator.compute(candidates, req.areaSqm());
        return new EstimateResult(
                CURRENCY,
                c.medianTotal(),
                c.rangeLow(),
                c.rangeHigh(),
                c.medianPps(),
                c.rangeLowPps(),
                c.rangeHighPps(),
                c.comparablesCount(),
                candidates.size(),
                c.confidenceScore(),
                c.confidenceLevel().name().toLowerCase(Locale.ROOT),
                c.warning(),
                c.retainedFacts().stream().map(ValuationService::toItem).toList());
    }

    private static ComparableItem toItem(ComparableFact f) {
        return new ComparableItem(
                f.id(),
                f.propertyType(),
                f.priceXof(),
                f.areaSqm(),
                BigDecimal.valueOf(f.priceXof()).divide(f.areaSqm(), PPS_SCALE, RoundingMode.HALF_UP),
                f.observedAt(),
                f.distanceMeters());
    }
}