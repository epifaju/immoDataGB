package com.immodata.gb.domain.confidence;

public sealed interface ConfidenceCondition
        permits SourceCountCondition,
                SourceTypeBonusCondition,
                VerifiedSourceBonusCondition,
                DocStatusBonusCondition,
                AgePenaltyCondition {
}