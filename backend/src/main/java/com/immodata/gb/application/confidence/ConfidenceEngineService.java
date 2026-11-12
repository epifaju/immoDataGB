package com.immodata.gb.application.confidence;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.confidence.ConfidenceCalculator;
import com.immodata.gb.domain.confidence.ConfidenceComputation;
import com.immodata.gb.domain.confidence.ConfidenceInput;
import com.immodata.gb.domain.confidence.ConfidenceRule;
import com.immodata.gb.domain.confidence.SourceFact;
import com.immodata.gb.infrastructure.persistence.confidence.ConfidenceRepository;
import com.immodata.gb.infrastructure.persistence.confidence.ConfidenceRepository.LockedProperty;
import com.immodata.gb.infrastructure.persistence.confidence.ConfidenceRuleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfidenceEngineService {

    private final ConfidenceRepository confidenceRepository;
    private final ConfidenceRuleRepository ruleRepository;
    private final Clock clock;

    public ConfidenceEngineService(
            ConfidenceRepository confidenceRepository, ConfidenceRuleRepository ruleRepository, Clock clock) {
        this.confidenceRepository = confidenceRepository;
        this.ruleRepository = ruleRepository;
        this.clock = clock;
    }

    @Transactional
    public ConfidenceResult recalculateProperty(UUID id) {
        LockedProperty property = confidenceRepository
                .lockActiveProperty(id)
                .orElseThrow(() -> new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        List<ConfidenceRule> rules = ruleRepository.listActiveParsed(property.organisationId());
        Map<UUID, List<SourceFact>> sources = confidenceRepository.loadSourceFacts(List.of(property.id()));
        return computeAndUpdate(property, rules, sources, today);
    }

    @Transactional
    public ConfidenceBatchResult recalculateOrganisation(UUID orgId) {
        if (!confidenceRepository.organisationExistsActive(orgId)) {
            throw new ApiException("ORGANISATION_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        LocalDate today = LocalDate.now(clock);
        List<LockedProperty> properties = confidenceRepository.lockActivePropertiesForOrg(orgId);
        List<ConfidenceRule> rules = ruleRepository.listActiveParsed(orgId);
        Map<UUID, List<SourceFact>> sources =
                confidenceRepository.loadSourceFacts(properties.stream().map(LockedProperty::id).toList());
        for (LockedProperty property : properties) {
            computeAndUpdate(property, rules, sources, today);
        }
        return new ConfidenceBatchResult(orgId, properties.size());
    }

    private ConfidenceResult computeAndUpdate(
            LockedProperty property,
            List<ConfidenceRule> rules,
            Map<UUID, List<SourceFact>> sources,
            LocalDate today) {
        ConfidenceInput input =
                new ConfidenceInput(property.docStatus(), sources.getOrDefault(property.id(), List.of()), today);
        ConfidenceComputation computation = ConfidenceCalculator.compute(input, rules);
        String level = computation.level().getSqlValue();
        confidenceRepository.updateConfidence(property.id(), computation.score(), level, computation.summary());
        return new ConfidenceResult(
                property.id(),
                computation.rawScore(),
                computation.score(),
                level,
                computation.summary(),
                computation.appliedRules().stream().map(AppliedRule::fromDomain).toList());
    }
}
