package com.immodata.gb.domain.confidence;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Gabarits du résumé de confiance, en portugais (langue produit par défaut). */
public final class ConfidenceSummaryFormatter {

    static final String NO_EVIDENCE = "Sem evidências suficientes para pontuar.";

    private static final Map<String, String> SOURCE_TYPE_LABELS = Map.of(
            "agency_listing", "anúncio de agência",
            "field_observation", "observação de campo",
            "scanned_doc", "documento digitalizado",
            "institutional", "fonte institucional",
            "third_party_user", "utilizador terceiro",
            "partner", "parceiro",
            "open_layer", "camada aberta");

    private static final Map<String, String> DOC_STATUS_LABELS = Map.of(
            "none", "inexistente",
            "declared", "declarada",
            "partial", "parcial",
            "verified", "verificada",
            "litigious", "em litígio");

    private ConfidenceSummaryFormatter() {
    }

    public static String format(
            int score, ConfidenceLevel level, List<ConfidenceRule> appliedRules, ConfidenceInput input) {
        if (appliedRules.isEmpty()) {
            return NO_EVIDENCE;
        }
        String criteria = appliedRules.stream()
                .map(rule -> criterion(rule, input) + " (" + signed(rule.scoreDelta()) + ")")
                .collect(Collectors.joining("; "));
        return "Pontuação " + score + "/100 (" + levelLabel(level) + "). Critérios: " + criteria + ".";
    }

    private static String criterion(ConfidenceRule rule, ConfidenceInput input) {
        return switch (rule.type()) {
            case SOURCE_COUNT -> {
                int units = input.units();
                yield units == 1 ? "1 unidade de fonte" : units + " unidades de fonte";
            }
            case SOURCE_TYPE_BONUS -> {
                String type = ((SourceTypeBonusCondition) rule.condition()).sourceType();
                yield "tipo de fonte: " + SOURCE_TYPE_LABELS.getOrDefault(type, type);
            }
            case VERIFIED_SOURCE_BONUS -> {
                int verified = input.verifiedCount();
                yield verified == 1 ? "1 fonte verificada" : verified + " fontes verificadas";
            }
            case DOC_STATUS_BONUS -> {
                String status = ((DocStatusBonusCondition) rule.condition()).docStatus();
                yield "documentação " + DOC_STATUS_LABELS.getOrDefault(status, status);
            }
            case AGE_PENALTY ->
                "fontes com mais de " + ((AgePenaltyCondition) rule.condition()).olderThanMonths() + " meses";
            case INCONSISTENCY_PENALTY -> throw new IllegalStateException("reserved rule type is never applied");
        };
    }

    private static String levelLabel(ConfidenceLevel level) {
        return switch (level) {
            case LOW -> "baixa";
            case MEDIUM -> "média";
            case HIGH -> "alta";
        };
    }

    private static String signed(int delta) {
        return delta >= 0 ? "+" + delta : "-" + (-delta);
    }
}