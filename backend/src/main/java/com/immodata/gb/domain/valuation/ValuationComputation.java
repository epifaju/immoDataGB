package com.immodata.gb.domain.valuation;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.math.BigDecimal;
import java.util.List;

/**
 * Pure calculator output. Totals are in XOF (HALF_UP); pps values are XOF/m² (scale 4).
 * {@code warning} is null when at least 5 comparables are retained.
 */
public record ValuationComputation(
        long rangeLow,
        long medianTotal,
        long rangeHigh,
        BigDecimal rangeLowPps,
        BigDecimal medianPps,
        BigDecimal rangeHighPps,
        int comparablesCount,
        int excludedOutliers,
        int confidenceScore,
        ConfidenceLevel confidenceLevel,
        String warning,
        List<ComparableFact> retainedFacts) {}
