package com.immodata.gb.application.field;

/**
 * Observation terrain validée par {@link FieldObservationRequestParser}. {@code title}, {@code occupationStatus} et
 * {@code notes} sont optionnels (null si absents ou vides).
 */
public record FieldObservationRequest(
        double latitude,
        double longitude,
        String propertyType,
        String title,
        String occupationStatus,
        String notes) {}
