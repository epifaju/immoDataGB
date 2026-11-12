package com.immodata.gb.domain.confidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfidenceCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    private static final LocalDate AUG = LocalDate.of(2026, 8, 10);

    private static final String NO_RULE_TEXT = "Sem evidências suficientes para pontuar.";

    // ---- helpers ----------------------------------------------------------------

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0004-%012d", n));
    }

    private static ConfidenceRule rule(int n, String name, ConfidenceRuleType type,
            ConfidenceCondition condition, int delta, int priority) {
        return new ConfidenceRule(id(n), name, type, condition, delta, priority);
    }

    private static List<ConfidenceRule> seedRules() {
        return List.of(
                rule(1, "source_units_1", ConfidenceRuleType.SOURCE_COUNT,
                        new SourceCountCondition(1, 1), 20, 10),
                rule(2, "source_units_2_plus", ConfidenceRuleType.SOURCE_COUNT,
                        new SourceCountCondition(2, null), 15, 20),
                rule(3, "field_observation_bonus", ConfidenceRuleType.SOURCE_TYPE_BONUS,
                        new SourceTypeBonusCondition("field_observation"), 20, 30),
                rule(4, "verified_source_bonus", ConfidenceRuleType.VERIFIED_SOURCE_BONUS,
                        new VerifiedSourceBonusCondition(1), 20, 35),
                rule(5, "doc_declared_bonus", ConfidenceRuleType.DOC_STATUS_BONUS,
                        new DocStatusBonusCondition("declared"), 10, 40),
                rule(6, "doc_partial_bonus", ConfidenceRuleType.DOC_STATUS_BONUS,
                        new DocStatusBonusCondition("partial"), 10, 50),
                rule(7, "doc_verified_bonus", ConfidenceRuleType.DOC_STATUS_BONUS,
                        new DocStatusBonusCondition("verified"), 25, 60),
                rule(8, "doc_litigious_penalty", ConfidenceRuleType.DOC_STATUS_BONUS,
                        new DocStatusBonusCondition("litigious"), -30, 70),
                rule(9, "stale_sources_penalty", ConfidenceRuleType.AGE_PENALTY,
                        new AgePenaltyCondition(24), -5, 80));
    }

    private static SourceFact fact(String type, int weight, boolean verified, LocalDate observedAt) {
        return new SourceFact(type, weight, verified, observedAt);
    }

    /** Seed shape: units 1 = agency; 5 = institutional(3)+field(2); 6 = all three. */
    private static ConfidenceInput seedProperty(int units, String docStatus, int verifiedCount) {
        List<SourceFact> sources = new ArrayList<>();
        switch (units) {
            case 1 -> sources.add(fact("agency_listing", 1, false, AUG));
            case 5 -> {
                sources.add(fact("institutional", 3, false, AUG));
                sources.add(fact("field_observation", 2, false, AUG));
            }
            case 6 -> {
                sources.add(fact("field_observation", 2, false, AUG));
                sources.add(fact("agency_listing", 1, false, AUG));
                sources.add(fact("institutional", 3, false, null));
            }
            default -> throw new IllegalArgumentException("units " + units);
        }
        for (int i = 0; i < verifiedCount; i++) {
            SourceFact s = sources.get(i);
            sources.set(i, fact(s.sourceType(), s.weight(), true, s.observedAt()));
        }
        return new ConfidenceInput(docStatus, sources, TODAY);
    }

    private static ConfidenceComputation compute(ConfidenceInput input, ConfidenceRule... rules) {
        return ConfidenceCalculator.compute(input, List.of(rules));
    }

    private static ConfidenceInput input(String docStatus, LocalDate today, SourceFact... sources) {
        return new ConfidenceInput(docStatus, List.of(sources), today);
    }

    private static List<String> names(ConfidenceComputation c) {
        return c.appliedRules().stream().map(AppliedRule::ruleName).toList();
    }

    // ---- source_count -----------------------------------------------------------

    @Test
    void sourceCountMatchesInclusiveBoundsAndSumsWeights() {
        ConfidenceRule r = rule(1, "sc", ConfidenceRuleType.SOURCE_COUNT,
                new SourceCountCondition(2, 4), 10, 0);
        assertTrue(compute(input("none", TODAY, fact("partner", 1, false, null)), r)
                .appliedRules().isEmpty(), "1 unit < min");
        assertEquals(1, compute(input("none", TODAY, fact("partner", 2, false, null)), r)
                .appliedRules().size(), "2 units = min");
        assertEquals(1, compute(input("none", TODAY,
                fact("partner", 3, false, null), fact("open_layer", 1, false, null)), r)
                .appliedRules().size(), "4 units = max");
        assertTrue(compute(input("none", TODAY,
                fact("partner", 3, false, null), fact("open_layer", 2, false, null)), r)
                .appliedRules().isEmpty(), "5 units > max");
    }

    @Test
    void sourceCountNullMaxHasNoCeiling() {
        ConfidenceRule r = rule(1, "sc", ConfidenceRuleType.SOURCE_COUNT,
                new SourceCountCondition(2, null), 10, 0);
        assertEquals(1, compute(input("none", TODAY, fact("partner", 100, false, null)), r)
                .appliedRules().size());
    }

    // ---- source_type_bonus ------------------------------------------------------

    @Test
    void sourceTypeBonusMatchesWhenAtLeastOneSourceOfThatType() {
        ConfidenceRule r = rule(1, "st", ConfidenceRuleType.SOURCE_TYPE_BONUS,
                new SourceTypeBonusCondition("field_observation"), 20, 0);
        assertEquals(1, compute(input("none", TODAY,
                fact("partner", 1, false, null), fact("field_observation", 1, false, null)), r)
                .appliedRules().size());
        assertTrue(compute(input("none", TODAY, fact("partner", 1, false, null)), r)
                .appliedRules().isEmpty());
    }

    // ---- verified_source_bonus --------------------------------------------------

    @Test
    void verifiedSourceBonusRequiresMinVerified() {
        ConfidenceRule one = rule(1, "v1", ConfidenceRuleType.VERIFIED_SOURCE_BONUS,
                new VerifiedSourceBonusCondition(1), 10, 0);
        ConfidenceRule two = rule(2, "v2", ConfidenceRuleType.VERIFIED_SOURCE_BONUS,
                new VerifiedSourceBonusCondition(2), 10, 0);
        ConfidenceInput oneVerified = input("none", TODAY,
                fact("partner", 1, true, null), fact("open_layer", 1, false, null));
        ConfidenceInput twoVerified = input("none", TODAY,
                fact("partner", 1, true, null), fact("open_layer", 1, true, null));
        ConfidenceInput none = input("none", TODAY, fact("partner", 1, false, null));

        assertEquals(1, compute(oneVerified, one).appliedRules().size());
        assertTrue(compute(oneVerified, two).appliedRules().isEmpty());
        assertEquals(1, compute(twoVerified, two).appliedRules().size());
        assertTrue(compute(none, one).appliedRules().isEmpty());
    }

    // ---- doc_status_bonus -------------------------------------------------------

    @Test
    void docStatusBonusMatchesEqualStatusOnly() {
        ConfidenceRule r = rule(1, "doc", ConfidenceRuleType.DOC_STATUS_BONUS,
                new DocStatusBonusCondition("partial"), 10, 0);
        assertEquals(1, compute(input("partial", TODAY), r).appliedRules().size());
        assertTrue(compute(input("declared", TODAY), r).appliedRules().isEmpty());
    }

    // ---- age_penalty ------------------------------------------------------------

    private static ConfidenceRule age(int months) {
        return rule(1, "age", ConfidenceRuleType.AGE_PENALTY, new AgePenaltyCondition(months), -5, 0);
    }

    @Test
    void agePenaltyExactlyNMonthsDoesNotMatchOneDayEarlierDoes() {
        LocalDate ref = LocalDate.of(2028, 8, 15);
        ConfidenceInput exact = input("none", ref, fact("partner", 1, false, LocalDate.of(2026, 8, 15)));
        ConfidenceInput earlier = input("none", ref, fact("partner", 1, false, LocalDate.of(2026, 8, 14)));
        assertTrue(compute(exact, age(24)).appliedRules().isEmpty());
        assertEquals(1, compute(earlier, age(24)).appliedRules().size());
    }

    @Test
    void agePenaltyNotAppliedWhenAnyDatedSourceIsRecent() {
        ConfidenceInput mixed = input("none", LocalDate.of(2028, 12, 1),
                fact("partner", 1, false, LocalDate.of(2026, 8, 1)),
                fact("open_layer", 1, false, LocalDate.of(2028, 11, 1)));
        assertTrue(compute(mixed, age(24)).appliedRules().isEmpty());
    }

    @Test
    void agePenaltyIgnoresUndatedSourcesButNeedsAtLeastOneDate() {
        LocalDate ref = LocalDate.of(2028, 12, 1);
        ConfidenceInput someUndated = input("none", ref,
                fact("partner", 1, false, null),
                fact("open_layer", 1, false, LocalDate.of(2026, 8, 1)));
        ConfidenceInput allUndated = input("none", ref,
                fact("partner", 1, false, null), fact("open_layer", 1, false, null));
        ConfidenceInput noSources = input("none", ref);
        assertEquals(1, compute(someUndated, age(24)).appliedRules().size());
        assertTrue(compute(allUndated, age(24)).appliedRules().isEmpty());
        assertTrue(compute(noSources, age(24)).appliedRules().isEmpty());
    }

    @Test
    void agePenaltyFixedClockMakesSeedStaleOnlyLater() {
        ConfidenceInput seed = seedProperty(1, "partial", 0);
        assertFalse(names(ConfidenceCalculator.compute(seed, seedRules())).contains("stale_sources_penalty"));
        ConfidenceInput later = new ConfidenceInput(seed.docStatus(), seed.sources(), LocalDate.of(2028, 12, 1));
        ConfidenceComputation c = ConfidenceCalculator.compute(later, seedRules());
        assertTrue(names(c).contains("stale_sources_penalty"));
        assertEquals(25, c.score());
    }

    // ---- clamp / order / empty / no sources ------------------------------------

    @Test
    void clampsLowAndKeepsRawScore() {
        ConfidenceComputation c = ConfidenceCalculator.compute(
                seedProperty(1, "litigious", 0), seedRules());
        assertEquals(-10, c.rawScore());
        assertEquals(0, c.score());
        assertEquals(ConfidenceLevel.LOW, c.level());
    }

    @Test
    void clampsHigh() {
        ConfidenceRule a = rule(1, "a", ConfidenceRuleType.DOC_STATUS_BONUS,
                new DocStatusBonusCondition("verified"), 100, 0);
        ConfidenceRule b = rule(2, "b", ConfidenceRuleType.DOC_STATUS_BONUS,
                new DocStatusBonusCondition("verified"), 50, 0);
        ConfidenceComputation c = compute(input("verified", TODAY), a, b);
        assertEquals(150, c.rawScore());
        assertEquals(100, c.score());
        assertEquals(ConfidenceLevel.HIGH, c.level());
    }

    @Test
    void priorityThenIdOrderAppliedRulesButNotScore() {
        ConfidenceRule late = rule(1, "late", ConfidenceRuleType.DOC_STATUS_BONUS,
                new DocStatusBonusCondition("partial"), 10, 50);
        ConfidenceRule earlyHighId = rule(9, "early_b", ConfidenceRuleType.DOC_STATUS_BONUS,
                new DocStatusBonusCondition("partial"), 5, 10);
        ConfidenceRule earlyLowId = rule(2, "early_a", ConfidenceRuleType.DOC_STATUS_BONUS,
                new DocStatusBonusCondition("partial"), 7, 10);
        ConfidenceInput in = input("partial", TODAY);

        ConfidenceComputation c1 = compute(in, late, earlyHighId, earlyLowId);
        ConfidenceComputation c2 = compute(in, earlyLowId, late, earlyHighId);
        assertEquals(List.of("early_a", "early_b", "late"), names(c1));
        assertEquals(names(c1), names(c2));
        assertEquals(22, c1.score());
        assertEquals(c1.score(), c2.score());
    }

    @Test
    void sortingDoesNotMutateCallerList() {
        List<ConfidenceRule> immutable = List.of(
                rule(2, "b", ConfidenceRuleType.DOC_STATUS_BONUS, new DocStatusBonusCondition("none"), 1, 20),
                rule(1, "a", ConfidenceRuleType.DOC_STATUS_BONUS, new DocStatusBonusCondition("none"), 1, 10));
        assertEquals(List.of("a", "b"), names(ConfidenceCalculator.compute(input("none", TODAY), immutable)));
    }

    @Test
    void noRulesAppliedGivesZeroLowAndFixedText() {
        ConfidenceComputation c = ConfidenceCalculator.compute(seedProperty(1, "none", 0), List.of());
        assertEquals(0, c.rawScore());
        assertEquals(0, c.score());
        assertEquals(ConfidenceLevel.LOW, c.level());
        assertTrue(c.appliedRules().isEmpty());
        assertEquals(NO_RULE_TEXT, c.summary());

        ConfidenceComputation noMatch = ConfidenceCalculator.compute(
                seedProperty(1, "none", 0),
                List.of(seedRules().get(7))); // litigious only
        assertEquals(NO_RULE_TEXT, noMatch.summary());
        assertTrue(noMatch.appliedRules().isEmpty());
    }

    @Test
    void propertyWithoutSourcesGetsDocRulesButNoSourceCountRules() {
        ConfidenceComputation c = ConfidenceCalculator.compute(
                new ConfidenceInput("partial", List.of(), TODAY), seedRules());
        assertEquals(List.of("doc_partial_bonus"), names(c));
        assertEquals(10, c.score());
        assertEquals("Pontuação 10/100 (baixa). Critérios: documentação parcial (+10).", c.summary());
    }

    @Test
    void zeroMinUnitsMatchesPropertyWithoutSources() {
        ConfidenceRule r = rule(1, "zero", ConfidenceRuleType.SOURCE_COUNT,
                new SourceCountCondition(0, null), 5, 0);
        ConfidenceComputation c = compute(input("none", TODAY), r);
        assertEquals(1, c.appliedRules().size());
        assertEquals("Pontuação 5/100 (baixa). Critérios: 0 unidades de fonte (+5).", c.summary());
    }

    @Test
    void inconsistencyPenaltyIsNeverEvaluated() {
        ConfidenceRule reserved = new ConfidenceRule(id(1), "inc",
                ConfidenceRuleType.INCONSISTENCY_PENALTY, null, -50, 0);
        assertEquals(NO_RULE_TEXT, compute(input("none", TODAY), reserved).summary());
    }

    @Test
    void appliedRuleCarriesNameTypeSqlValueAndDelta() {
        ConfidenceComputation c = ConfidenceCalculator.compute(seedProperty(1, "partial", 0), seedRules());
        assertEquals(List.of(
                new AppliedRule("source_units_1", "source_count", 20),
                new AppliedRule("doc_partial_bonus", "doc_status_bonus", 10)), c.appliedRules());
        assertEquals(c.rawScore(), c.appliedRules().stream().mapToInt(AppliedRule::scoreDelta).sum());
    }

    // ---- rule / condition invariants -------------------------------------------

    @Test
    void ruleRejectsTypeConditionMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceRule(id(1), "x",
                ConfidenceRuleType.AGE_PENALTY, new DocStatusBonusCondition("none"), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceRule(id(1), "x",
                ConfidenceRuleType.SOURCE_COUNT, null, 1, 0));
    }

    @Test
    void conditionsRejectInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new SourceCountCondition(-1, null));
        assertThrows(IllegalArgumentException.class, () -> new SourceCountCondition(3, 2));
        assertThrows(IllegalArgumentException.class, () -> new VerifiedSourceBonusCondition(0));
        assertThrows(IllegalArgumentException.class, () -> new AgePenaltyCondition(0));
    }

    @Test
    void ruleTypeRoundTripsSqlValues() {
        assertEquals(6, ConfidenceRuleType.values().length);
        for (ConfidenceRuleType t : ConfidenceRuleType.values()) {
            assertEquals(t, ConfidenceRuleType.fromSql(t.getSqlValue()).orElseThrow());
        }
        assertTrue(ConfidenceRuleType.fromSql("nope").isEmpty());
    }

    // ---- seed: 24 properties ----------------------------------------------------

    /** n, units, docStatus, verified, rawScore, score, level */
    private static final Object[][] EXPECTED = {
        {1, 5, "none", 0, 35, 35, ConfidenceLevel.MEDIUM},
        {2, 6, "declared", 0, 45, 45, ConfidenceLevel.MEDIUM},
        {3, 1, "partial", 0, 30, 30, ConfidenceLevel.LOW},
        {4, 5, "verified", 1, 80, 80, ConfidenceLevel.HIGH},
        {5, 6, "litigious", 0, 5, 5, ConfidenceLevel.LOW},
        {6, 1, "none", 0, 20, 20, ConfidenceLevel.LOW},
        {7, 5, "declared", 0, 45, 45, ConfidenceLevel.MEDIUM},
        {8, 6, "partial", 0, 45, 45, ConfidenceLevel.MEDIUM},
        {9, 1, "verified", 1, 65, 65, ConfidenceLevel.MEDIUM},
        {10, 5, "litigious", 1, 25, 25, ConfidenceLevel.LOW},
        {11, 6, "none", 1, 55, 55, ConfidenceLevel.MEDIUM},
        {12, 1, "declared", 1, 50, 50, ConfidenceLevel.MEDIUM},
        {13, 5, "partial", 0, 45, 45, ConfidenceLevel.MEDIUM},
        {14, 6, "verified", 1, 80, 80, ConfidenceLevel.HIGH},
        {15, 1, "litigious", 0, -10, 0, ConfidenceLevel.LOW},
        {16, 5, "none", 0, 35, 35, ConfidenceLevel.MEDIUM},
        {17, 6, "declared", 0, 45, 45, ConfidenceLevel.MEDIUM},
        {18, 1, "partial", 0, 30, 30, ConfidenceLevel.LOW},
        {19, 5, "verified", 1, 80, 80, ConfidenceLevel.HIGH},
        {20, 6, "litigious", 0, 5, 5, ConfidenceLevel.LOW},
        {21, 1, "none", 1, 40, 40, ConfidenceLevel.MEDIUM},
        {22, 5, "declared", 1, 65, 65, ConfidenceLevel.MEDIUM},
        {23, 6, "partial", 1, 65, 65, ConfidenceLevel.MEDIUM},
        {24, 1, "verified", 1, 65, 65, ConfidenceLevel.MEDIUM},
    };

    @Test
    void seedTable24ScoresAndLevels() {
        int medium = 0;
        int low = 0;
        int high = 0;
        for (Object[] row : EXPECTED) {
            ConfidenceInput in = seedProperty((int) row[1], (String) row[2], (int) row[3]);
            ConfidenceComputation c = ConfidenceCalculator.compute(in, seedRules());
            String label = "bien " + row[0];
            assertEquals((int) row[4], c.rawScore(), label + " raw");
            assertEquals((int) row[5], c.score(), label + " score");
            assertEquals(row[6], c.level(), label + " level");
            assertFalse(names(c).contains("stale_sources_penalty"), label + " stale");
            switch (c.level()) {
                case MEDIUM -> medium++;
                case LOW -> low++;
                case HIGH -> high++;
            }
        }
        assertEquals(14, medium);
        assertEquals(7, low);
        assertEquals(3, high);
    }

    @Test
    void seedTable24WithClock2028AllStale() {
        int medium = 0;
        int low = 0;
        int high = 0;
        for (Object[] row : EXPECTED) {
            ConfidenceInput base = seedProperty((int) row[1], (String) row[2], (int) row[3]);
            ConfidenceInput in = new ConfidenceInput(base.docStatus(), base.sources(), LocalDate.of(2028, 12, 1));
            ConfidenceComputation c = ConfidenceCalculator.compute(in, seedRules());
            assertTrue(names(c).contains("stale_sources_penalty"), "bien " + row[0]);
            assertEquals((int) row[4] - 5, c.rawScore(), "bien " + row[0]);
            if (row[0].equals(3)) {
                assertEquals(25, c.score());
            }
            switch (c.level()) {
                case MEDIUM -> medium++;
                case LOW -> low++;
                case HIGH -> high++;
            }
        }
        assertEquals(12, medium);
        assertEquals(9, low);
        assertEquals(3, high);
    }

    @Test
    void seedRuleAppliedCountsAcross24() {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Object[] row : EXPECTED) {
            ConfidenceComputation c = ConfidenceCalculator.compute(
                    seedProperty((int) row[1], (String) row[2], (int) row[3]), seedRules());
            for (String n : names(c)) {
                counts.merge(n, 1, Integer::sum);
            }
        }
        assertEquals(16, counts.get("source_units_2_plus"));
        assertEquals(16, counts.get("field_observation_bonus"));
        assertEquals(11, counts.get("verified_source_bonus"));
        assertEquals(8, counts.get("source_units_1"));
        assertEquals(5, counts.get("doc_declared_bonus"));
        assertEquals(5, counts.get("doc_partial_bonus"));
        assertEquals(5, counts.get("doc_verified_bonus"));
        assertEquals(4, counts.get("doc_litigious_penalty"));
        assertFalse(counts.containsKey("stale_sources_penalty"));
    }

    // ---- 7 reference summaries, character for character -------------------------

    private static String summaryOf(int n) {
        for (Object[] row : EXPECTED) {
            if ((int) row[0] == n) {
                return ConfidenceCalculator.compute(
                        seedProperty((int) row[1], (String) row[2], (int) row[3]), seedRules()).summary();
            }
        }
        throw new IllegalArgumentException("bien " + n);
    }

    @Test
    void summaryBien1() {
        assertEquals("Pontuação 35/100 (média). Critérios: 5 unidades de fonte (+15); "
                + "tipo de fonte: observação de campo (+20).", summaryOf(1));
    }

    @Test
    void summaryBien2() {
        assertEquals("Pontuação 45/100 (média). Critérios: 6 unidades de fonte (+15); "
                + "tipo de fonte: observação de campo (+20); documentação declarada (+10).", summaryOf(2));
    }

    @Test
    void summaryBien3() {
        assertEquals("Pontuação 30/100 (baixa). Critérios: 1 unidade de fonte (+20); "
                + "documentação parcial (+10).", summaryOf(3));
    }

    @Test
    void summaryBien4() {
        assertEquals("Pontuação 80/100 (alta). Critérios: 5 unidades de fonte (+15); "
                + "tipo de fonte: observação de campo (+20); 1 fonte verificada (+20); "
                + "documentação verificada (+25).", summaryOf(4));
    }

    @Test
    void summaryBien5() {
        assertEquals("Pontuação 5/100 (baixa). Critérios: 6 unidades de fonte (+15); "
                + "tipo de fonte: observação de campo (+20); documentação em litígio (-30).", summaryOf(5));
    }

    @Test
    void summaryBien9() {
        assertEquals("Pontuação 65/100 (média). Critérios: 1 unidade de fonte (+20); "
                + "1 fonte verificada (+20); documentação verificada (+25).", summaryOf(9));
    }

    @Test
    void summaryBien15() {
        assertEquals("Pontuação 0/100 (baixa). Critérios: 1 unidade de fonte (+20); "
                + "documentação em litígio (-30).", summaryOf(15));
    }

    @Test
    void summaryUsesPluralVerifiedSourcesAndUnits() {
        ConfidenceRule verified = rule(1, "v", ConfidenceRuleType.VERIFIED_SOURCE_BONUS,
                new VerifiedSourceBonusCondition(1), 20, 10);
        ConfidenceRule count = rule(2, "c", ConfidenceRuleType.SOURCE_COUNT,
                new SourceCountCondition(1, null), 15, 20);
        ConfidenceInput in = input("none", TODAY,
                fact("partner", 2, true, null), fact("open_layer", 3, true, null));
        assertEquals("Pontuação 35/100 (média). Critérios: 2 fontes verificadas (+20); "
                + "5 unidades de fonte (+15).", compute(in, verified, count).summary());
    }

    @Test
    void summaryAgePenaltyText() {
        ConfidenceInput in = input("none", LocalDate.of(2028, 12, 1),
                fact("partner", 1, false, LocalDate.of(2026, 1, 1)));
        assertEquals("Pontuação 0/100 (baixa). Critérios: fontes com mais de 24 meses (-5).",
                compute(in, age(24)).summary());
    }
}
