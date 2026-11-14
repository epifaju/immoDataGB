package com.immodata.gb.application.imports;

import java.time.Instant;
import java.util.UUID;

public record ImportJobStatus(
        UUID jobId,
        String status,
        String originalFilename,
        int totalRows,
        int successRows,
        int errorRows,
        String errorMessage,
        Instant createdAt) {}
