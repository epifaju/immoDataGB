package com.immodata.gb.application.valuation;

import java.math.BigDecimal;

public record EstimateRequest(
        String propertyType,
        BigDecimal areaSqm,
        double latitude,
        double longitude,
        int radiusMeters,
        int periodMonths) {}