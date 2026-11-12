package com.immodata.gb.application.property;

import java.time.LocalDate;
import java.util.UUID;

public record SourceItem(
        UUID id,
        String sourceType,
        String label,
        LocalDate observedAt,
        boolean isVerified,
        int weight) {}
