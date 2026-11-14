package com.immodata.gb.application.admin;

import java.time.Instant;
import java.util.UUID;

public record AuditEventView(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        String oldValue,
        String newValue,
        String summary,
        Instant createdAt) {}
