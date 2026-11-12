package com.immodata.gb.domain.confidence;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fonction pure : aucune base de données, aucune horloge (la date du jour est dans l'entrée). */
public final class ConfidenceCalculator {

    // Ordre (priority ASC, id ASC) ; l'id est comparé en texte hexadécimal minuscule, ce qui
    // correspond à l'ordre octet par octet non signé de PostgreSQL.
    private static final Comparator<ConfidenceRule> RULE_ORDER = Comparator
            .comparingInt(ConfidenceRule::priority)
            .thenComparing(rule -> rule.id().toString());

    private ConfidenceCalculator() {
    }

    public static ConfidenceComputation compute(ConfidenceInput input, List<ConfidenceRule> rules) {
        List<ConfidenceRule> ordered = new ArrayList<>(rules);
        ordered.sort(RULE_ORDER);

        List<ConfidenceRule> matched = new ArrayList<>();
        int rawScore = 0;
        for (ConfidenceRule rule : ordered) {
            if (matches(rule, input)) {
                matched.add(rule);
                rawScore += rule.scoreDelta();
            }
        }

        int score = Math.max(0, Math.min(100, rawScore));
        ConfidenceLevel level = ConfidenceLevel.fromScore(score);
        List<AppliedRule> applied = matched.stream()
                .map(rule -> new AppliedRule(rule.name(), rule.type().getSqlValue(), rule.scoreDelta()))
                .toList();
        String summary = ConfidenceSummaryFormatter.format(score, level, matched, input);
        return new ConfidenceComputation(rawScore, score, level, applied, summary);
    }

    private static boolean matches(ConfidenceRule rule, ConfidenceInput input) {
        ConfidenceCondition condition = rule.condition();
        if (condition == null) {
            return false; // inconsistency_penalty : réservé, jamais évalué
        }
        return switch (condition) {
            case SourceCountCondition c -> {
                int units = input.units();
                yield units >= c.minUnits() && (c.maxUnits() == null || units <= c.maxUnits());
            }
            case SourceTypeBonusCondition c ->
                input.sources().stream().anyMatch(s -> s.sourceType().equals(c.sourceType()));
            case VerifiedSourceBonusCondition c -> input.verifiedCount() >= c.minVerified();
            case DocStatusBonusCondition c -> input.docStatus().equals(c.docStatus());
            case AgePenaltyCondition c -> matchesAge(c, input);
        };
    }

    private static boolean matchesAge(AgePenaltyCondition condition, ConfidenceInput input) {
        LocalDate threshold = input.today().minusMonths(condition.olderThanMonths());
        List<LocalDate> dates = input.sources().stream()
                .map(SourceFact::observedAt)
                .filter(d -> d != null)
                .toList();
        return !dates.isEmpty() && dates.stream().allMatch(d -> d.isBefore(threshold));
    }
}