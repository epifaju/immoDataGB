package com.immodata.gb.application.valuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ComparableItem(
        UUID id,
        String propertyType,
        long priceXof,
        BigDecimal areaSqm,
        BigDecimal pricePerSqm,
        LocalDate observedAt,
        double distanceMeters) {}