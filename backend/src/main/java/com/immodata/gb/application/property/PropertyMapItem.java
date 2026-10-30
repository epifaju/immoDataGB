package com.immodata.gb.application.property;

import java.util.UUID;

public record PropertyMapItem(
        UUID id,
        UUID organisationId,
        String reference,
        String title,
        String propertyType,
        int confidenceScore,
        String confidenceLevel,
        String confidenceSummary,
        double longitude,
        double latitude) {}
