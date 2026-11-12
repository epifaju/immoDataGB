package com.immodata.gb.domain.confidence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.immodata.gb.domain.property.ConfidenceLevel;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfidenceSummaryFormatterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0004-000000000001");

    private static ConfidenceRule rule(ConfidenceRuleType type, ConfidenceCondition c, int delta) {
        return new ConfidenceRule(ID, "r", type, c, delta, 0);
    }

    @ParameterizedTest
    @CsvSource({
        "agency_listing,anúncio de agência",
        "field_observation,observação de campo",
        "scanned_doc,documento digitalizado",
        "institutional,fonte institucional",
        "third_party_user,utilizador terceiro",
        "partner,parceiro",
        "open_layer,camada aberta"
    })
    void sourceTypeLabels(String type, String label) {
        String s = ConfidenceSummaryFormatter.format(10, ConfidenceLevel.LOW,
                List.of(rule(ConfidenceRuleType.SOURCE_TYPE_BONUS, new SourceTypeBonusCondition(type), 10)),
                new ConfidenceInput("none", List.of(), TODAY));
        assertEquals("Pontuação 10/100 (baixa). Critérios: tipo de fonte: " + label + " (+10).", s);
    }

    @ParameterizedTest
    @CsvSource({
        "none,inexistente",
        "declared,declarada",
        "partial,parcial",
        "verified,verificada",
        "litigious,em litígio"
    })
    void docStatusLabels(String status, String label) {
        String s = ConfidenceSummaryFormatter.format(10, ConfidenceLevel.LOW,
                List.of(rule(ConfidenceRuleType.DOC_STATUS_BONUS, new DocStatusBonusCondition(status), 10)),
                new ConfidenceInput(status, List.of(), TODAY));
        assertEquals("Pontuação 10/100 (baixa). Critérios: documentação " + label + " (+10).", s);
    }

    @Test
    void levelLabels() {
        List<ConfidenceRule> one = List.of(
                rule(ConfidenceRuleType.DOC_STATUS_BONUS, new DocStatusBonusCondition("none"), 5));
        ConfidenceInput in = new ConfidenceInput("none", List.of(), TODAY);
        assertEquals("Pontuação 5/100 (baixa). Critérios: documentação inexistente (+5).",
                ConfidenceSummaryFormatter.format(5, ConfidenceLevel.LOW, one, in));
        assertEquals("Pontuação 50/100 (média). Critérios: documentação inexistente (+5).",
                ConfidenceSummaryFormatter.format(50, ConfidenceLevel.MEDIUM, one, in));
        assertEquals("Pontuação 90/100 (alta). Critérios: documentação inexistente (+5).",
                ConfidenceSummaryFormatter.format(90, ConfidenceLevel.HIGH, one, in));
    }

    @Test
    void deltaIsSignedAsciiAndZeroIsPlus() {
        ConfidenceInput in = new ConfidenceInput("none", List.of(), TODAY);
        String s = ConfidenceSummaryFormatter.format(0, ConfidenceLevel.LOW, List.of(
                rule(ConfidenceRuleType.DOC_STATUS_BONUS, new DocStatusBonusCondition("none"), -30),
                rule(ConfidenceRuleType.DOC_STATUS_BONUS, new DocStatusBonusCondition("none"), 0)), in);
        assertEquals("Pontuação 0/100 (baixa). Critérios: documentação inexistente (-30); "
                + "documentação inexistente (+0).", s);
    }

    @Test
    void emptyAppliedRulesGivesFixedText() {
        assertEquals("Sem evidências suficientes para pontuar.",
                ConfidenceSummaryFormatter.format(0, ConfidenceLevel.LOW, List.of(),
                        new ConfidenceInput("none", List.of(), TODAY)));
    }

    @Test
    void singularAndPluralForms() {
        ConfidenceRule units = rule(ConfidenceRuleType.SOURCE_COUNT, new SourceCountCondition(0, null), 1);
        ConfidenceRule ver = rule(ConfidenceRuleType.VERIFIED_SOURCE_BONUS, new VerifiedSourceBonusCondition(1), 1);
        ConfidenceInput oneUnitOneVerified = new ConfidenceInput("none",
                List.of(new SourceFact("partner", 1, true, null)), TODAY);
        ConfidenceInput manyUnitsManyVerified = new ConfidenceInput("none",
                List.of(new SourceFact("partner", 1, true, null), new SourceFact("partner", 2, true, null)), TODAY);
        assertEquals("Pontuação 2/100 (baixa). Critérios: 1 unidade de fonte (+1); 1 fonte verificada (+1).",
                ConfidenceSummaryFormatter.format(2, ConfidenceLevel.LOW, List.of(units, ver), oneUnitOneVerified));
        assertEquals("Pontuação 2/100 (baixa). Critérios: 3 unidades de fonte (+1); 2 fontes verificadas (+1).",
                ConfidenceSummaryFormatter.format(2, ConfidenceLevel.LOW, List.of(units, ver), manyUnitsManyVerified));
    }
}
