package com.immodata.gb.application.confidence;

import java.util.List;
import java.util.UUID;

public record ConfidenceResult(
        UUID propertyId,
        int rawScore,
        int confidenceScore,
        String confidenceLevel,
        String confidenceSummary,
        List<AppliedRule> appliedRules) {
}
