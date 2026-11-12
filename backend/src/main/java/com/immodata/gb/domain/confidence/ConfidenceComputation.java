package com.immodata.gb.domain.confidence;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.util.List;

public record ConfidenceComputation(
        int rawScore,
        int score,
        ConfidenceLevel level,
        List<AppliedRule> appliedRules,
        String summary) {

    public ConfidenceComputation {
        appliedRules = List.copyOf(appliedRules);
    }
}