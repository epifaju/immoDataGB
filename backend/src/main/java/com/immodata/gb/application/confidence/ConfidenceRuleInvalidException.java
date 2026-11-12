package com.immodata.gb.application.confidence;

import java.util.UUID;

/**
 * Règle de confiance active dont la définition en base est invalide. Le recalcul échoue (500
 * {@code INTERNAL_ERROR}) avant toute mise à jour ; l'identifiant et le nom servent à la journalisation
 * côté serveur uniquement.
 */
public class ConfidenceRuleInvalidException extends RuntimeException {

    private final UUID ruleId;
    private final String ruleName;

    public ConfidenceRuleInvalidException(UUID ruleId, String ruleName, Throwable cause) {
        super("Invalid confidence rule " + ruleId + " (" + ruleName + "): "
                + (cause == null ? "unknown" : cause.getMessage()), cause);
        this.ruleId = ruleId;
        this.ruleName = ruleName;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }
}
