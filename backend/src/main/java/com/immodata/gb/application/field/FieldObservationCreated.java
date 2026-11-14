package com.immodata.gb.application.field;

import java.util.UUID;

public record FieldObservationCreated(
        UUID id,
        String reference,
        String title,
        String propertyType,
        String validationStatus,
        double latitude,
        double longitude,
        int confidenceScore,
        String confidenceLevel) {}
