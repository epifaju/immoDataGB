package com.immodata.gb.domain.confidence;

import java.util.Objects;
import java.util.UUID;

/**
 * Règle de confiance chargée depuis la base. {@code condition} est obligatoire et doit correspondre au
 * {@code type} ; seul le type réservé {@code INCONSISTENCY_PENALTY} n'a pas de condition.
 */
public record ConfidenceRule(
        UUID id,
        String name,
        ConfidenceRuleType type,
        ConfidenceCondition condition,
        int scoreDelta,
        int priority) {

    public ConfidenceRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        boolean consistent = switch (type) {
            case SOURCE_COUNT -> condition instanceof SourceCountCondition;
            case SOURCE_TYPE_BONUS -> condition instanceof SourceTypeBonusCondition;
            case VERIFIED_SOURCE_BONUS -> condition instanceof VerifiedSourceBonusCondition;
            case DOC_STATUS_BONUS -> condition instanceof DocStatusBonusCondition;
            case AGE_PENALTY -> condition instanceof AgePenaltyCondition;
            case INCONSISTENCY_PENALTY -> condition == null;
        };
        if (!consistent) {
            throw new IllegalArgumentException("condition does not match rule type " + type);
        }
    }
}