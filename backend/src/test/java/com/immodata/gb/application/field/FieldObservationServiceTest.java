package com.immodata.gb.application.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.config.FieldProperties;
import com.immodata.gb.infrastructure.persistence.field.FieldObservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class FieldObservationServiceTest {

    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PROPERTY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T23:59:59Z"), ZoneOffset.UTC);

    private final FieldObservationRepository repository = mock(FieldObservationRepository.class);

    private FieldObservationService service(boolean enabled, UUID org) {
        return new FieldObservationService(repository, new FieldProperties(enabled, org), CLOCK);
    }

    private static FieldObservationRequest request(String title, String occupation, String notes) {
        return new FieldObservationRequest(11.863, -15.598, "house", title, occupation, notes);
    }

    private void stubInsert() {
        when(repository.insertPending(
                        any(UUID.class), anyString(), anyString(), anyString(), anyDouble(), anyDouble(), any()))
                .thenReturn(PROPERTY_ID);
    }

    private record Captured(String reference, String title, String label) {}

    private Captured captureInsert(UUID org, String type, double lat, double lng) {
        ArgumentCaptor<String> reference = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(repository)
                .insertPending(
                        eq(org),
                        reference.capture(),
                        title.capture(),
                        eq(type),
                        eq(lat),
                        eq(lng),
                        label.capture());
        return new Captured(reference.getValue(), title.getValue(), label.getValue());
    }

    @Test
    void mutationsDisabledReturnsNotFoundWithoutTouchingRepository() {
        FieldObservationService service = service(false, ORG);

        assertThatThrownBy(() -> service.create(request(null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verifyNoInteractions(repository);
    }

    @Test
    void disabledTakesPrecedenceOverMissingOrganisation() {
        FieldObservationService service = service(false, null);

        assertThatThrownBy(() -> service.create(request(null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
        verifyNoInteractions(repository);
    }

    @Test
    void missingOrganisationIsInternalError() {
        FieldObservationService service = service(true, null);

        assertThatThrownBy(() -> service.create(request(null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("FIELD_ORGANISATION_MISSING");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        verifyNoInteractions(repository);
    }

    @Test
    void createsPendingObservationWithInitialConfidence() {
        stubInsert();

        FieldObservationCreated created = service(true, ORG).create(request("Casa azul", "occupied", "Portão verde"));

        Captured captured = captureInsert(ORG, "house", 11.863, -15.598);
        assertThat(created.id()).isEqualTo(PROPERTY_ID);
        assertThat(created.reference()).isEqualTo(captured.reference());
        assertThat(created.title()).isEqualTo("Casa azul");
        assertThat(created.propertyType()).isEqualTo("house");
        assertThat(created.validationStatus()).isEqualTo("pending_validation");
        assertThat(created.latitude()).isEqualTo(11.863);
        assertThat(created.longitude()).isEqualTo(-15.598);
        assertThat(created.confidenceScore()).isEqualTo(12);
        assertThat(created.confidenceLevel()).isEqualTo("low");
        assertThat(captured.title()).isEqualTo("Casa azul");
    }

    @Test
    void referenceUsesUtcDateFromClockAndEightLowerHex() {
        stubInsert();

        service(true, ORG).create(request(null, null, null));

        Captured captured = captureInsert(ORG, "house", 11.863, -15.598);
        assertThat(captured.reference()).matches("FLD-20261114-[0-9a-f]{8}");
    }

    @Test
    void referencesDifferBetweenCalls() {
        stubInsert();
        FieldObservationService service = service(true, ORG);

        FieldObservationCreated first = service.create(request(null, null, null));
        FieldObservationCreated second = service.create(request(null, null, null));

        assertThat(first.reference()).isNotEqualTo(second.reference());
    }

    @Test
    void defaultTitleIsPortugueseWithReference() {
        stubInsert();

        FieldObservationCreated created = service(true, ORG).create(request(null, null, null));

        assertThat(created.title()).isEqualTo("Observação de campo (" + created.reference() + ")");
        assertThat(captureInsert(ORG, "house", 11.863, -15.598).title()).isEqualTo(created.title());
    }

    @Test
    void blankTitleFallsBackToDefault() {
        stubInsert();

        FieldObservationCreated created = service(true, ORG).create(request("  ", null, null));

        assertThat(created.title()).startsWith("Observação de campo (FLD-");
    }

    @Test
    void labelCombinesOccupationAndNotes() {
        stubInsert();

        service(true, ORG).create(request(null, "vacant", "Portão verde"));

        assertThat(captureInsert(ORG, "house", 11.863, -15.598).label()).isEqualTo("[vacant] Portão verde");
    }

    @Test
    void labelIsNotesOnlyWithoutOccupation() {
        stubInsert();

        service(true, ORG).create(request(null, null, "Portão verde"));

        assertThat(captureInsert(ORG, "house", 11.863, -15.598).label()).isEqualTo("Portão verde");
    }

    @Test
    void labelIsOccupationOnlyWithoutNotes() {
        stubInsert();

        service(true, ORG).create(request(null, "unknown", null));

        assertThat(captureInsert(ORG, "house", 11.863, -15.598).label()).isEqualTo("[unknown]");
    }

    @Test
    void labelIsNullWhenNoOccupationNorNotes() {
        stubInsert();

        service(true, ORG).create(request(null, null, null));

        assertThat(captureInsert(ORG, "house", 11.863, -15.598).label()).isNull();
    }

    @Test
    void labelIsNullWhenNotesBlankAndNoOccupation() {
        stubInsert();

        service(true, ORG).create(request(null, null, "   "));

        assertThat(captureInsert(ORG, "house", 11.863, -15.598).label()).isNull();
    }

    @Test
    void labelTruncatedTo200Characters() {
        stubInsert();

        service(true, ORG).create(request(null, "occupied", "n".repeat(500)));

        String label = captureInsert(ORG, "house", 11.863, -15.598).label();
        assertThat(label).hasSize(200).startsWith("[occupied] nnn");
    }

    @Test
    void labelWithoutOccupationTruncatedTo200Characters() {
        stubInsert();

        service(true, ORG).create(request(null, null, "n".repeat(250)));

        assertThat(captureInsert(ORG, "house", 11.863, -15.598).label()).hasSize(200);
    }
}
