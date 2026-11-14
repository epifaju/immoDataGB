package com.immodata.gb.domain.imports;

public record ImportCsvRow(
        int rowNumber,
        String reference,
        String title,
        String propertyType,
        String addressText,
        double latitude,
        double longitude) {}
