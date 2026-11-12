package com.immodata.gb.application.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.domain.confidence.ConfidenceRule;
import com.immodata.gb.domain.confidence.ConfidenceRuleType;
import com.immodata.gb.domain.confidence.DocStatusBonusCondition;
import com.immodata.gb.domain.confidence.SourceFact;
import com.immodata.gb.infrastructure.persistence.confidence.ConfidenceRepository;
import com.immodata.gb.infrastructure.persistence.confidence.ConfidenceRepository.LockedProperty;
import com.immodata.gb.infrastructure.persistence.confidence.ConfidenceRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfidenceEngineServiceTest {

    private final ConfidenceRepository repo = mock(ConfidenceRepository.class);
    private final ConfidenceRuleRepository ruleRepo = mock(ConfidenceRuleRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
    private final ConfidenceEngineService service = new ConfidenceEngineService(repo, ruleRepo, clock);

    private final UUID orgId = UUID.randomUUID();
    private final UUID propId = UUID.randomUUID();
    private final ConfidenceRule rule = new ConfidenceRule(
            UUID.randomUUID(), "Doc verified", ConfidenceRuleType.DOC_STATUS_BONUS,
            new DocStatusBonusCondition("verified"), 40, 10);

    @Test
    void recalculateProperty_updatesAndMapsAppliedRules() {
        when(repo.lockActiveProperty(propId)).thenReturn(Optional.of(new LockedProperty(propId, orgId, "verified")));
        when(ruleRepo.listActiveParsed(orgId)).thenReturn(List.of(rule));
        when(repo.loadSourceFacts(List.of(propId))).thenReturn(Map.of());

        ConfidenceResult result = service.recalculateProperty(propId);

        assertThat(result.propertyId()).isEqualTo(propId);
        assertThat(result.rawScore()).isEqualTo(40);
        assertThat(result.confidenceScore()).isEqualTo(40);
        assertThat(result.confidenceLevel()).isEqualTo("medium");
        assertThat(result.confidenceSummary()).isNotBlank();
        assertThat(result.appliedRules())
                .containsExactly(new AppliedRule("Doc verified", "doc_status_bonus", 40));
        verify(repo).updateConfidence(propId, 40, "medium", result.confidenceSummary());
    }

    @Test
    void recalculateProperty_missing_throwsPropertyNotFound() {
        when(repo.lockActiveProperty(propId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recalculateProperty(propId))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("PROPERTY_NOT_FOUND"));
        verify(repo, never()).updateConfidence(any(), anyInt(), anyString(), anyString());
    }

    @Test
    void recalculateOrganisation_missing_throwsOrganisationNotFound() {
        when(repo.organisationExistsActive(orgId)).thenReturn(false);

        assertThatThrownBy(() -> service.recalculateOrganisation(orgId))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("ORGANISATION_NOT_FOUND"));
    }

    @Test
    void recalculateOrganisation_loadsRulesAndSourcesOnce_updatesEach() {
        UUID p2 = UUID.randomUUID();
        when(repo.organisationExistsActive(orgId)).thenReturn(true);
        when(repo.lockActivePropertiesForOrg(orgId)).thenReturn(List.of(
                new LockedProperty(propId, orgId, "verified"), new LockedProperty(p2, orgId, "none")));
        when(ruleRepo.listActiveParsed(orgId)).thenReturn(List.of(rule));
        when(repo.loadSourceFacts(any())).thenReturn(Map.of(propId, List.<SourceFact>of()));

        ConfidenceBatchResult result = service.recalculateOrganisation(orgId);

        assertThat(result).isEqualTo(new ConfidenceBatchResult(orgId, 2));
        verify(ruleRepo, times(1)).listActiveParsed(orgId);
        verify(repo, times(1)).loadSourceFacts(any());
        verify(repo, times(2)).updateConfidence(any(), anyInt(), anyString(), anyString());
    }
}
