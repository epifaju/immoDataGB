package com.immodata.gb.application.admin;

import com.immodata.gb.api.PageResponse;
import com.immodata.gb.api.error.ApiException;
import com.immodata.gb.infrastructure.config.AdminProperties;
import com.immodata.gb.infrastructure.persistence.admin.AdminValidationRepository;
import com.immodata.gb.infrastructure.persistence.admin.AuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * File de validation et journal d'audit. Validate et reject exigent {@code app.admin.mutations-enabled}.
 * Les lectures restent ouvertes.
 */
@Service
public class AdminValidationService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private static final String ENTITY_TYPE_PROPERTY = "property";
    private static final String ACTION_VALIDATE = "validate";
    private static final String ACTION_REJECT = "reject";
    private static final String VALIDATE_OLD = "{\"validationStatus\":\"pending_validation\"}";
    private static final String VALIDATE_NEW = "{\"validationStatus\":\"validated\"}";
    private static final String REJECT_OLD = "{\"validationStatus\":\"pending_validation\",\"deleted\":false}";
    private static final String REJECT_NEW = "{\"deleted\":true}";

    private final AdminValidationRepository validationRepository;
    private final AuditEventRepository auditEventRepository;
    private final AdminProperties properties;

    public AdminValidationService(
            AdminValidationRepository validationRepository,
            AuditEventRepository auditEventRepository,
            AdminProperties properties) {
        this.validationRepository = validationRepository;
        this.auditEventRepository = auditEventRepository;
        this.properties = properties;
    }

    public PageResponse<ValidationQueueItem> listQueue(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return validationRepository.findPendingQueue(safePage, safeSize);
    }

    public PageResponse<AuditEventView> listAudit(
            String entityType, String action, Instant from, Instant to, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return auditEventRepository.find(entityType, action, from, to, safePage, safeSize);
    }

    @Transactional
    public ValidationQueueItem validate(UUID id) {
        ValidationQueueItem item = requirePending(id);
        validationRepository.markValidated(item.id());
        auditEventRepository.insert(
                ENTITY_TYPE_PROPERTY,
                item.id(),
                ACTION_VALIDATE,
                VALIDATE_OLD,
                VALIDATE_NEW,
                "Bem validado (" + item.reference() + ").");
        return item;
    }

    @Transactional
    public RejectResult reject(UUID id) {
        ValidationQueueItem item = requirePending(id);
        validationRepository.softDelete(item.id());
        auditEventRepository.insert(
                ENTITY_TYPE_PROPERTY,
                item.id(),
                ACTION_REJECT,
                REJECT_OLD,
                REJECT_NEW,
                "Bem rejeitado (" + item.reference() + ").");
        return new RejectResult(item.id(), true);
    }

    private ValidationQueueItem requirePending(UUID id) {
        if (!properties.mutationsEnabled()) {
            throw new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        return validationRepository
                .findPendingItemById(id)
                .orElseThrow(() -> new ApiException("PROPERTY_NOT_FOUND", HttpStatus.NOT_FOUND));
    }
}
