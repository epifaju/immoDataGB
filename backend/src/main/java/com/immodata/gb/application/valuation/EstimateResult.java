package com.immodata.gb.application.valuation;

import java.math.BigDecimal;
import java.util.List;

/** Estimation toujours exprimée en fourchette (rangeLow/rangeHigh) autour de la médiane. */
public record EstimateResult(
        String currency,
        long medianTotal,
        long rangeLow,
        long rangeHigh,
        BigDecimal medianPricePerSqm,
        BigDecimal rangeLowPricePerSqm,
        BigDecimal rangeHighPricePerSqm,
        int comparablesCount,
        int comparablesRawCount,
        int confidenceScore,
        String confidenceLevel,
        String warning,
        List<ComparableItem> comparables) {}