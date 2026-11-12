package com.immodata.gb.infrastructure.persistence.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.immodata.gb.application.confidence.ConfidenceRuleInvalidException;
import com.immodata.gb.domain.confidence.AgePenaltyCondition;
import com.immodata.gb.domain.confidence.ConfidenceRule;
import com.immodata.gb.domain.confidence.ConfidenceRuleType;
import com.immodata.gb.domain.confidence.DocStatusBonusCondition;
import com.immodata.gb.domain.confidence.SourceCountCondition;
import com.immodata.gb.domain.confidence.SourceTypeBonusCondition;
import com.immodata.gb.domain.confidence.VerifiedSourceBonusCondition;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConfidenceConditionReaderTest {

    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String RULE_NAME = "rule_under_test";

    private final ConfidenceConditionReader reader = new ConfidenceConditionReader();

    private ConfidenceRule read(String type, String json) {
        return reader.read(RULE_ID, RULE_NAME, type, json, 10, 5);
    }

    private void assertInvalid(String type, String json) {
        assertThatThrownBy(() -> read(type, json))
                .isInstanceOfSatisfying(ConfidenceRuleInvalidException.class, e -> {
                    assertThat(e.getRuleId()).isEqualTo(RULE_ID);
                    assertThat(e.getRuleName()).isEqualTo(RULE_NAME);
                    assertThat(e.getCause()).isNotNull();
                });
    }

    // --- valid conditions -------------------------------------------------------------------

    @Test
    void readsSourceCountWithCeiling() {
        ConfidenceRule rule = read("source_count", "{\"minUnits\":1,\"maxUnits\":3}");
        assertThat(rule.type()).isEqualTo(ConfidenceRuleType.SOURCE_COUNT);
        assertThat(rule.condition()).isEqualTo(new SourceCountCondition(1, 3));
        assertThat(rule.id()).isEqualTo(RULE_ID);
        assertThat(rule.name()).isEqualTo(RULE_NAME);
        assertThat(rule.scoreDelta()).isEqualTo(10);
        assertThat(rule.priority()).isEqualTo(5);
    }

    @Test
    void readsSourceCountWithNullCeiling() {
        assertThat(read("source_count", "{\"minUnits\":2,\"maxUnits\":null}").condition())
                .isEqualTo(new SourceCountCondition(2, null));
    }

    @Test
    void readsSourceCountWithMinZeroAndMaxEqualMin() {
        assertThat(read("source_count", "{\"minUnits\":0,\"maxUnits\":0}").condition())
                .isEqualTo(new SourceCountCondition(0, 0));
    }

    @Test
    void readsSourceCountIgnoringKeyOrderAndWhitespace() {
        assertThat(read("source_count", " { \"maxUnits\" : null , \"minUnits\" : 4 } ").condition())
                .isEqualTo(new SourceCountCondition(4, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "agency_listing", "field_observation", "scanned_doc", "institutional",
        "third_party_user", "partner", "open_layer"
    })
    void readsEverySourceType(String sourceType) {
        assertThat(read("source_type_bonus", "{\"sourceType\":\"" + sourceType + "\"}").condition())
                .isEqualTo(new SourceTypeBonusCondition(sourceType));
    }

    @Test
    void readsVerifiedSourceBonus() {
        assertThat(read("verified_source_bonus", "{\"minVerified\":1}").condition())
                .isEqualTo(new VerifiedSourceBonusCondition(1));
        assertThat(read("verified_source_bonus", "{\"minVerified\":2}").condition())
                .isEqualTo(new VerifiedSourceBonusCondition(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "declared", "partial", "verified", "litigious"})
    void readsEveryDocStatus(String docStatus) {
        assertThat(read("doc_status_bonus", "{\"docStatus\":\"" + docStatus + "\"}").condition())
                .isEqualTo(new DocStatusBonusCondition(docStatus));
    }

    @Test
    void readsAgePenalty() {
        assertThat(read("age_penalty", "{\"olderThanMonths\":24}").condition())
                .isEqualTo(new AgePenaltyCondition(24));
        assertThat(read("age_penalty", "{\"olderThanMonths\":1}").condition())
                .isEqualTo(new AgePenaltyCondition(1));
    }

    @Test
    void reservedInconsistencyPenaltyHasNoCondition() {
        ConfidenceRule rule = read("inconsistency_penalty", "{}");
        assertThat(rule.type()).isEqualTo(ConfidenceRuleType.INCONSISTENCY_PENALTY);
        assertThat(rule.condition()).isNull();
    }

    // --- unknown keys -----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "source_count|{\"minUnits\":1,\"maxUnits\":null,\"extra\":1}",
        "source_type_bonus|{\"sourceType\":\"partner\",\"extra\":true}",
        "verified_source_bonus|{\"minVerified\":1,\"extra\":\"x\"}",
        "doc_status_bonus|{\"docStatus\":\"partial\",\"extra\":null}",
        "age_penalty|{\"olderThanMonths\":24,\"extra\":0}",
        "source_count|{\"minUnits\":1,\"maxUnits\":null,\"MinUnits\":1}"
    })
    void rejectsUnknownKeys(String type, String json) {
        assertInvalid(type, json);
    }

    @Test
    void rejectsKeyFromAnotherRuleType() {
        assertInvalid("age_penalty", "{\"minVerified\":1}");
        assertInvalid("verified_source_bonus", "{\"olderThanMonths\":24}");
    }

    // --- missing keys -----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "source_count|{}",
        "source_count|{\"minUnits\":1}",
        "source_count|{\"maxUnits\":null}",
        "source_type_bonus|{}",
        "verified_source_bonus|{}",
        "doc_status_bonus|{}",
        "age_penalty|{}"
    })
    void rejectsMissingKeys(String type, String json) {
        assertInvalid(type, json);
    }

    // --- invalid types ----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "source_count|{\"minUnits\":\"x\",\"maxUnits\":null}",
        "source_count|{\"minUnits\":\"1\",\"maxUnits\":null}",
        "source_count|{\"minUnits\":1,\"maxUnits\":\"3\"}",
        "source_count|{\"minUnits\":null,\"maxUnits\":null}",
        "source_count|{\"minUnits\":1.5,\"maxUnits\":null}",
        "source_count|{\"minUnits\":true,\"maxUnits\":null}",
        "source_count|{\"minUnits\":[1],\"maxUnits\":null}",
        "source_count|{\"minUnits\":99999999999,\"maxUnits\":null}",
        "verified_source_bonus|{\"minVerified\":null}",
        "verified_source_bonus|{\"minVerified\":\"1\"}",
        "age_penalty|{\"olderThanMonths\":null}",
        "age_penalty|{\"olderThanMonths\":\"24\"}",
        "age_penalty|{\"olderThanMonths\":24.0}",
        "source_type_bonus|{\"sourceType\":1}",
        "source_type_bonus|{\"sourceType\":null}",
        "doc_status_bonus|{\"docStatus\":null}",
        "doc_status_bonus|{\"docStatus\":false}"
    })
    void rejectsInvalidValueTypes(String type, String json) {
        assertInvalid(type, json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[1]", "\"x\"", "42", "null", "", "{", "{\"minUnits\":1,\"maxUnits\":null} {}"})
    void rejectsNonObjectOrMalformedJson(String json) {
        assertInvalid("source_count", json);
    }

    @Test
    void rejectsDuplicateKeys() {
        assertInvalid("source_count", "{\"minUnits\":1,\"minUnits\":2,\"maxUnits\":null}");
    }

    @Test
    void rejectsNullConditionJson() {
        assertInvalid("source_count", null);
    }

    // --- unknown enum values ------------------------------------------------------------------

    @Test
    void rejectsUnknownSourceType() {
        assertInvalid("source_type_bonus", "{\"sourceType\":\"unknown_type\"}");
        assertInvalid("source_type_bonus", "{\"sourceType\":\"Partner\"}");
        assertInvalid("source_type_bonus", "{\"sourceType\":\"\"}");
    }

    @Test
    void rejectsUnknownDocStatus() {
        assertInvalid("doc_status_bonus", "{\"docStatus\":\"unknown\"}");
        assertInvalid("doc_status_bonus", "{\"docStatus\":\"VERIFIED\"}");
        assertInvalid("doc_status_bonus", "{\"docStatus\":\"\"}");
    }

    @Test
    void rejectsUnknownRuleType() {
        assertInvalid("not_a_type", "{}");
        assertInvalid("", "{}");
        assertInvalid(null, "{}");
    }

    // --- bounds -------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "source_count|{\"minUnits\":-1,\"maxUnits\":null}",
        "source_count|{\"minUnits\":3,\"maxUnits\":2}",
        "source_count|{\"minUnits\":1,\"maxUnits\":-5}",
        "verified_source_bonus|{\"minVerified\":0}",
        "verified_source_bonus|{\"minVerified\":-1}",
        "age_penalty|{\"olderThanMonths\":0}",
        "age_penalty|{\"olderThanMonths\":-12}"
    })
    void rejectsBoundViolationsAsRuleInvalid(String type, String json) {
        assertInvalid(type, json);
    }

    @Test
    void boundViolationWrapsIllegalArgumentException() {
        assertThatThrownBy(() -> read("age_penalty", "{\"olderThanMonths\":0}"))
                .isInstanceOf(ConfidenceRuleInvalidException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exceptionMessageCarriesRuleIdAndName() {
        assertThatThrownBy(() -> read("age_penalty", "{}"))
                .hasMessageContaining(RULE_ID.toString())
                .hasMessageContaining(RULE_NAME);
    }
}
