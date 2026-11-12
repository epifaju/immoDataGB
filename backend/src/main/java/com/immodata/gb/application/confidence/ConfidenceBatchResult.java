package com.immodata.gb.application.confidence;

import java.util.UUID;

public record ConfidenceBatchResult(UUID organisationId, int recalculatedCount) {
}
