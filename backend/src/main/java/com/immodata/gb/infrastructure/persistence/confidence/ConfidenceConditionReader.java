package com.immodata.gb.infrastructure.persistence.confidence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.immodata.gb.application.confidence.ConfidenceRuleInvalidException;
import com.immodata.gb.domain.confidence.AgePenaltyCondition;
import com.immodata.gb.domain.confidence.ConfidenceCondition;
import com.immodata.gb.domain.confidence.ConfidenceRule;
import com.immodata.gb.domain.confidence.ConfidenceRuleType;
import com.immodata.gb.domain.confidence.DocStatusBonusCondition;
import com.immodata.gb.domain.confidence.SourceCountCondition;
import com.immodata.gb.domain.confidence.SourceTypeBonusCondition;
import com.immodata.gb.domain.confidence.VerifiedSourceBonusCondition;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture stricte de {@code condition_json} : clés inconnues ou manquantes, null/texte/décimal pour un
 * entier, doublons de clés, énumérations inconnues et bornes invalides sont refusés. Toute anomalie est
 * convertie en {@link ConfidenceRuleInvalidException}.
 */
final class ConfidenceConditionReader {

    private static final Set<String> DOC_STATUSES = Set.of("none", "declared", "partial", "verified", "litigious");
    private static final Set<String> SOURCE_TYPES = Set.of(
            "agency_listing",
            "field_observation",
            "scanned_doc",
            "institutional",
            "third_party_user",
            "partner",
            "open_layer");

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    ConfidenceRule read(
            UUID ruleId, String ruleName, String ruleType, String conditionJson, int scoreDelta, int priority) {
        try {
            ConfidenceRuleType type = ConfidenceRuleType.fromSql(ruleType)
                    .orElseThrow(() -> new IllegalArgumentException("unknown rule_type: " + ruleType));
            ConfidenceCondition condition = parseCondition(type, conditionJson);
            return new ConfidenceRule(ruleId, ruleName, type, condition, scoreDelta, priority);
        } catch (ConfidenceRuleInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfidenceRuleInvalidException(ruleId, ruleName, e);
        }
    }

    private ConfidenceCondition parseCondition(ConfidenceRuleType type, String json) throws Exception {
        if (json == null) {
            throw new IllegalArgumentException("condition_json is null");
        }
        if (!mapper.readTree(json).isObject()) {
            throw new IllegalArgumentException("condition_json must be a JSON object");
        }
        return switch (type) {
            case SOURCE_COUNT -> mapper.readValue(json, SourceCountCondition.class);
            case SOURCE_TYPE_BONUS -> {
                SourceTypeBonusCondition c = mapper.readValue(json, SourceTypeBonusCondition.class);
                requireKnown(SOURCE_TYPES, c.sourceType(), "sourceType");
                yield c;
            }
            case VERIFIED_SOURCE_BONUS -> mapper.readValue(json, VerifiedSourceBonusCondition.class);
            case DOC_STATUS_BONUS -> {
                DocStatusBonusCondition c = mapper.readValue(json, DocStatusBonusCondition.class);
                requireKnown(DOC_STATUSES, c.docStatus(), "docStatus");
                yield c;
            }
            case AGE_PENALTY -> mapper.readValue(json, AgePenaltyCondition.class);
            // Réservé : jamais évalué, donc sans condition typée.
            case INCONSISTENCY_PENALTY -> null;
        };
    }

    private static void requireKnown(Set<String> allowed, String value, String field) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("unknown " + field + ": " + value);
        }
    }
}
