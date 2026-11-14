package com.immodata.gb.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.config.AdminProperties;
import com.immodata.gb.infrastructure.persistence.admin.AdminValidationRepository;
import com.immodata.gb.infrastructure.persistence.admin.AuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

class AdminValidationServiceTest {

    private static final UUID PROPERTY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String REFERENCE = "FLD-20261114-abcd1234";
    private static final String VALIDATE_OLD = "{\"validationStatus\":\"pending_validation\"}";
    private static final String VALIDATE_NEW = "{\"validationStatus\":\"validated\"}";
    private static final String REJECT_OLD = "{\"validationStatus\":\"pending_validation\",\"deleted\":false}";
    private static final String REJECT_NEW = "{\"deleted\":true}";

    private final AdminValidationRepository validationRepository = mock(AdminValidationRepository.class);
    private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);

    private AdminValidationService service(boolean mutationsEnabled) {
        return new AdminValidationService(
                validationRepository, auditEventRepository, new AdminProperties(mutationsEnabled));
    }

    private static ValidationQueueItem pendingItem() {
        return new ValidationQueueItem(
                PROPERTY_ID,
                REFERENCE,
                "Casa azul",
                "house",
                Instant.parse("2026-11-14T10:15:30Z"),
                11.863,
                -15.598);
    }

    @Test
    void mutationsDisabledValidateReturnsNotFoundWithoutTouchingRepositories() {
        assertThatThrownBy(() -> service(false).validate(PROPERTY_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verifyNoInteractions(validationRepository, auditEventRepository);
    }

    @Test
    void mutationsDisabledRejectReturnsNotFoundWithoutTouchingRepositories() {
        assertThatThrownBy(() -> service(false).reject(PROPERTY_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verifyNoInteractions(validationRepository, auditEventRepository);
    }

    @Test
    void validateMissingPendingReturnsPropertyNotFoundWithoutWrites() {
        when(validationRepository.findPendingItemById(PROPERTY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).validate(PROPERTY_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("PROPERTY_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verify(validationRepository).findPendingItemById(PROPERTY_ID);
        verify(validationRepository, never()).markValidated(any());
        verify(validationRepository, never()).softDelete(any());
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void rejectMissingPendingReturnsPropertyNotFoundWithoutWrites() {
        when(validationRepository.findPendingItemById(PROPERTY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).reject(PROPERTY_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("PROPERTY_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verify(validationRepository).findPendingItemById(PROPERTY_ID);
        verify(validationRepository, never()).markValidated(any());
        verify(validationRepository, never()).softDelete(any());
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void validateMarksValidatedAndInsertsExactAudit() {
        ValidationQueueItem item = pendingItem();
        when(validationRepository.findPendingItemById(PROPERTY_ID)).thenReturn(Optional.of(item));

        ValidationQueueItem result = service(true).validate(PROPERTY_ID);

        assertThat(result).isSameAs(item);
        InOrder order = inOrder(validationRepository, auditEventRepository);
        order.verify(validationRepository).findPendingItemById(PROPERTY_ID);
        order.verify(validationRepository).markValidated(PROPERTY_ID);
        order.verify(auditEventRepository)
                .insert(
                        eq("property"),
                        eq(PROPERTY_ID),
                        eq("validate"),
                        eq(VALIDATE_OLD),
                        eq(VALIDATE_NEW),
                        eq("Bem validado (" + REFERENCE + ")."));
        verify(validationRepository, never()).softDelete(any());
    }

    @Test
    void rejectSoftDeletesAndInsertsExactAudit() {
        ValidationQueueItem item = pendingItem();
        when(validationRepository.findPendingItemById(PROPERTY_ID)).thenReturn(Optional.of(item));

        RejectResult result = service(true).reject(PROPERTY_ID);

        assertThat(result).isEqualTo(new RejectResult(PROPERTY_ID, true));
        InOrder order = inOrder(validationRepository, auditEventRepository);
        order.verify(validationRepository).findPendingItemById(PROPERTY_ID);
        order.verify(validationRepository).softDelete(PROPERTY_ID);
        order.verify(auditEventRepository)
                .insert(
                        eq("property"),
                        eq(PROPERTY_ID),
                        eq("reject"),
                        eq(REJECT_OLD),
                        eq(REJECT_NEW),
                        eq("Bem rejeitado (" + REFERENCE + ")."));
        verify(validationRepository, never()).markValidated(any());
    }

    @Test
    void listQueueClampsPageAndSizeWithoutFlagGuard() {
        PageResponse<ValidationQueueItem> clamped = PageResponse.of(0, 100, 0, List.of());
        PageResponse<ValidationQueueItem> defaultSize = PageResponse.of(2, 20, 0, List.of());
        when(validationRepository.findPendingQueue(0, 100)).thenReturn(clamped);
        when(validationRepository.findPendingQueue(2, 20)).thenReturn(defaultSize);

        assertThat(service(false).listQueue(-3, 1000)).isSameAs(clamped);
        assertThat(service(false).listQueue(2, 0)).isSameAs(defaultSize);

        verify(validationRepository).findPendingQueue(0, 100);
        verify(validationRepository).findPendingQueue(2, 20);
        verify(validationRepository, never()).findPendingQueue(anyInt(), eq(1000));
        verify(validationRepository, never()).findPendingQueue(anyInt(), eq(0));
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void listAuditClampsPageAndSizeAndForwardsFiltersWithoutFlagGuard() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-14T23:59:59Z");
        PageResponse<AuditEventView> clamped = PageResponse.of(0, 100, 0, List.of());
        PageResponse<AuditEventView> defaultSize = PageResponse.of(3, 20, 0, List.of());
        when(auditEventRepository.find("property", "validate", from, to, 0, 100)).thenReturn(clamped);
        when(auditEventRepository.find(null, null, null, null, 3, 20)).thenReturn(defaultSize);

        assertThat(service(false).listAudit("property", "validate", from, to, -1, 250))
                .isSameAs(clamped);
        assertThat(service(false).listAudit(null, null, null, null, 3, 0)).isSameAs(defaultSize);

        verify(auditEventRepository).find("property", "validate", from, to, 0, 100);
        verify(auditEventRepository).find(null, null, null, null, 3, 20);
        verify(auditEventRepository, never()).find(any(), any(), any(), any(), anyInt(), eq(250));
        verifyNoInteractions(validationRepository);
    }

    @Test
    void validateAndRejectAreTransactional() throws Exception {
        Transactional validateTx =
                AdminValidationService.class.getMethod("validate", UUID.class).getAnnotation(Transactional.class);
        Transactional rejectTx =
                AdminValidationService.class.getMethod("reject", UUID.class).getAnnotation(Transactional.class);

        assertThat(validateTx).isNotNull();
        assertThat(validateTx.readOnly()).isFalse();
        assertThat(rejectTx).isNotNull();
        assertThat(rejectTx.readOnly()).isFalse();
    }
}
