package com.immodata.gb.application.admin;

import java.time.Instant;
import java.util.UUID;

public record ValidationQueueItem(
        UUID id,
        String reference,
        String title,
        String propertyType,
        Instant createdAt,
        double latitude,
        double longitude) {}
