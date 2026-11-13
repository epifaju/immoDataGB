package com.immodata.gb.domain.valuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Observed comparable sale/listing, already filtered spatially and by type/date. */
public record ComparableFact(
        UUID id,
        String propertyType,
        long priceXof,
        BigDecimal areaSqm,
        LocalDate observedAt,
        double distanceMeters) {}
