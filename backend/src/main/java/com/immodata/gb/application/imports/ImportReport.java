package com.immodata.gb.application.imports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Même payload que {@link ImportJobStatus} + erreurs par ligne. */
public record ImportReport(
        UUID jobId,
        String status,
        String originalFilename,
        int totalRows,
        int successRows,
        int errorRows,
        String errorMessage,
        Instant createdAt,
        List<ImportRowErrorItem> errors) {

    public static ImportReport of(ImportJobStatus job, List<ImportRowErrorItem> errors) {
        return new ImportReport(
                job.jobId(),
                job.status(),
                job.originalFilename(),
                job.totalRows(),
                job.successRows(),
                job.errorRows(),
                job.errorMessage(),
                job.createdAt(),
                errors);
    }
}
