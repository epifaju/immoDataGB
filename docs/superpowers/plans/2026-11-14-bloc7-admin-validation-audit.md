# Bloc 7 — Validation queue + audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** File admin des biens `pending_validation` avec validate/reject + journal `audit_event` filtrable + pages `/admin/validation-queue` et `/admin/audit`.

**Architecture:** `V13` crée `audit_event`. `AdminValidationService` valide (→ `validated` + audit) ou rejette (soft-delete + audit) derrière `app.admin.mutations-enabled`. GET queue/audit ouverts. Frontend deux pages + nav.

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, JdbcClient, PostGIS, Next.js 14.2, TanStack Query, next-intl, Vitest, Testcontainers PostGIS.

**Spec:** `docs/superpowers/specs/2026-11-14-bloc7-admin-validation-audit-design.md`.

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, AOP, dashboard KPIs, Playwright, statut `rejected`.
- JdbcClient uniquement.
- POST validate/reject derrière **`app.admin.mutations-enabled`** (404 `NOT_FOUND` si off). GET queue + audit toujours ouverts.
- Validate → `validation_status=validated` ; Reject → `deleted_at=now()` ; audit actions `validate`|`reject` seulement.
- Summaries PT exactes : `Bem validado ({reference}).` / `Bem rejeitado ({reference}).`
- JSON audit exacts spec § API.
- Shell PowerShell ; commits `git.exe commit -F` UTF-8 **sans BOM**. Ne pas modifier `V1`–`V12`.

---

## File map

**Créer :**
- `backend/src/main/resources/db/migration/V13__create_audit_event.sql`
- `infrastructure/config/AdminProperties.java` (+ `AdminPropertiesTest`)
- `application/admin/ValidationQueueItem.java`
- `application/admin/AuditEventView.java`
- `application/admin/RejectResult.java`
- `application/admin/AdminValidationService.java` (+ test)
- `infrastructure/persistence/admin/AdminValidationRepository.java`
- `infrastructure/persistence/admin/AuditEventRepository.java`
- `api/admin/AdminValidationController.java` (+ WebMvcTest)
- `api/admin/AdminAuditController.java` (+ WebMvcTest)
- `api/admin/AdminValidationIT.java`, `AdminMutationsDisabledIT.java`
- `frontend/app/[locale]/admin/validation-queue/page.tsx`
- `frontend/app/[locale]/admin/audit/page.tsx`
- `frontend/components/admin/ValidationQueuePanel.tsx` (+ test)
- `frontend/components/admin/AuditLogPanel.tsx` (+ test)
- `frontend/lib/api/admin.ts`
- `frontend/messages/{pt,fr,en}/admin.json`

**Modifier :**
- `application.yml`, `application-dev.yml`
- `frontend/lib/api/types.ts`
- `frontend/messages/{pt,fr,en}/common.json`, `errors.json`
- `frontend/i18n/request.ts`, `layout.tsx`, `messages.test.ts`
- `frontend/.env.example`
- `README.md`

---

### Task 1: `AdminProperties` + YAML

**Files:**
- Create: `AdminProperties.java`, `AdminPropertiesTest.java`
- Modify: `application.yml`, `application-dev.yml`

**Interfaces:**
```java
@ConfigurationProperties("app.admin")
public record AdminProperties(
    @DefaultValue("false") boolean mutationsEnabled
) {}
```
```yaml
# application.yml
app:
  admin:
    mutations-enabled: false

# application-dev.yml
app:
  admin:
    mutations-enabled: true
```
- Scan via `@ConfigurationPropertiesScan` existant (comme Field/Import).
- Test : défaut `false` ; property override `true`.

- [ ] **Step 1: Properties + YAML + test.**

- [ ] **Step 2: Commit**

```text
feat: add admin mutations properties
```

---

### Task 2: Migration V13

**Files:**
- Create: `V13__create_audit_event.sql`

**Interfaces:** SQL exact spec § Modèle :

```sql
CREATE TABLE audit_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_type VARCHAR(50) NOT NULL,
  entity_id UUID NOT NULL,
  action VARCHAR(50) NOT NULL,
  performed_by UUID NULL,
  ip_address INET NULL,
  old_value JSONB NULL,
  new_value JSONB NULL,
  summary TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT audit_event_action_check CHECK (
    action IN ('validate', 'reject')
  )
);

CREATE INDEX audit_event_entity_idx
  ON audit_event (entity_type, entity_id);

CREATE INDEX audit_event_created_idx
  ON audit_event (created_at DESC);
```

- [ ] **Step 1: Écrire V13.**

- [ ] **Step 2: Commit**

```text
feat: add audit_event schema
```

---

### Task 3: Repositories + DTOs

**Files:**
- Create: `ValidationQueueItem`, `AuditEventView`, `RejectResult`, `AdminValidationRepository`, `AuditEventRepository`
- Couverture IT = Task 6 (pas d’IT repo isolé).

**Interfaces:**
```java
public record ValidationQueueItem(
    UUID id, String reference, String title, String propertyType,
    Instant createdAt, double latitude, double longitude) {}

public record AuditEventView(
    UUID id, String entityType, UUID entityId, String action,
    String oldValue, String newValue, String summary, Instant createdAt) {}
// oldValue/newValue = JSON text from JSONB (::text) for thin slice

public record RejectResult(UUID id, boolean deleted) {}

// AdminValidationRepository
PageResponse<ValidationQueueItem> findPendingQueue(int page, int size);
Optional<PendingProperty> findPendingById(UUID id);
// PendingProperty: id, reference, validationStatus (deleted_at IS NULL)
void markValidated(UUID id);
void softDelete(UUID id);

// AuditEventRepository
void insert(String entityType, UUID entityId, String action,
            String oldJson, String newJson, String summary);
PageResponse<AuditEventView> find(
    String entityType, String action, Instant from, Instant to,
    int page, int size);
```
- Queue SQL : `deleted_at IS NULL AND validation_status = 'pending_validation'` ; ORDER BY `created_at DESC` ; lat/lng via `ST_Y`/`ST_X` sur geom point (même pattern spatial existant).
- `entity_type` insert = `"property"`.
- Pagination : `page`/`size` clamp comme imports (`size` max 100).

- [ ] **Step 1: Implémenter records + repos JdbcClient.**

- [ ] **Step 2: Commit**

```text
feat: persist admin validation queue and audit events
```

---

### Task 4: `AdminValidationService` (TDD)

**Files:**
- Create: `AdminValidationService.java`, `AdminValidationServiceTest.java`

**Interfaces:**
```java
PageResponse<ValidationQueueItem> listQueue(int page, int size);

ValidationQueueItem validate(UUID id);
// mutations off → NOT_FOUND
// absent / soft-deleted / pas pending → PROPERTY_NOT_FOUND
// markValidated + audit validate
// old: {"validationStatus":"pending_validation"}
// new: {"validationStatus":"validated"}
// summary: "Bem validado ({reference})."

RejectResult reject(UUID id);
// mêmes gardes
// softDelete + audit reject
// old: {"validationStatus":"pending_validation","deleted":false}
// new: {"deleted":true}
// summary: "Bem rejeitado ({reference})."
```
- Flag check **dans le service** (comme Field/Import) avant load.
- `@Transactional` sur `validate` / `reject`.
- `listQueue` : pas de garde flag (GET ouvert).

- [ ] **Step 1: Tests RED (flag, not pending, validate audit payload, reject audit payload).**

- [ ] **Step 2: Implémenter service GREEN.**

- [ ] **Step 3: Commit**

```text
feat: validate or reject pending properties with audit
```

---

### Task 5: Controllers + WebMvcTest

**Files:**
- Create: `AdminValidationController`, `AdminAuditController` + tests WebMvc

**Interfaces:**
```text
GET  /api/v1/admin/validation-queue?page=&size=
     → 200 ApiResponse<PageResponse<ValidationQueueItem>>

POST /api/v1/admin/properties/{id}/validate
     → 200 ApiResponse<ValidationQueueItem>   (ou détail minimal équivalent)
     flag off → 404 NOT_FOUND (controller ou service ; cohérent Field)

POST /api/v1/admin/properties/{id}/reject
     → 200 ApiResponse<RejectResult>
     flag off → 404 NOT_FOUND

GET  /api/v1/admin/audit?entityType=&action=&from=&to=&page=&size=
     → 200 ApiResponse<PageResponse<AuditEventView>>
     query invalide → 400 ADMIN_REQUEST_INVALID
```
- `UuidParam` / parsing UUID invalide → 400 cohérent projet.
- Double garde flag (controller early + service) OK comme FieldObservationController.

- [ ] **Step 1: WebMvc TDD + controllers.**

- [ ] **Step 2: Commit**

```text
feat: expose admin validation and audit endpoints
```

---

### Task 6: IT PostGIS

**Files:**
- Create: `AdminValidationIT.java`, `AdminMutationsDisabledIT.java`

**Interfaces:**
- DynamicPropertySource : `app.admin.mutations-enabled=true` (+ field mutations true pour seed pending via POST field).
- Flow :
  1. POST field-observation → pending id A
  2. GET validation-queue → contient A
  3. POST validate A → 200 ; GET spatial list contient référence A ; GET audit `action=validate` contient A
  4. POST field-observation → pending id B
  5. POST reject B → 200 `{deleted:true}` ; B absent queue + spatial ; GET `/properties/{B}` → 404 ; audit `action=reject`
- Mutations disabled IT : POST validate/reject → 404 ; GET queue + audit → 200
- `.\mvnw.cmd verify` — BUILD SUCCESS

- [ ] **Step 1: IT + verify.**

- [ ] **Step 2: Commit**

```text
test: cover admin validate reject and audit against PostGIS
```

---

### Task 7: Frontend pages admin

**Files:**
- Create: pages, panels, `lib/api/admin.ts`, `messages/*/admin.json` (+ Vitest)
- Modify: `types.ts`, `common.json`, `errors.json`, `i18n/request.ts`, `layout.tsx`, `messages.test.ts`, `.env.example`

**Interfaces:**
```ts
// types
ValidationQueueItem, AuditEventView, RejectResult
// admin.ts
fetchValidationQueue({page,size})
validateProperty(id)
rejectProperty(id)
fetchAudit({entityType?,action?,from?,to?,page,size})
```
- Nav (clés PT d’abord) :
  - `nav.validationQueue` : pt `Fila de validação` / fr `File de validation` / en `Validation queue`
  - `nav.audit` : pt `Auditoria` / fr `Audit` / en `Audit`
- Layout links : `/admin/validation-queue`, `/admin/audit`
- Actions Validate/Reject visibles si `NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED === 'true'`
- Après succès : invalidate queue (+ audit queries)
- Vitest : boutons selon flag ; filtres audit (mock fetch)
- `npm test` PASS
- `.env.example` : `NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED=true`

- [ ] **Step 1: UI + i18n + api client + tests.**

- [ ] **Step 2: Commit**

```text
feat: add admin validation queue and audit pages
```

---

### Task 8: README + vérif finale

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Documenter curl validate/reject, GET queue/audit, flags `app.admin` + `NEXT_PUBLIC_ADMIN_*`, pages, reject=soft-delete (pas statut rejected).**

- [ ] **Step 2: `.\mvnw.cmd verify` + `npm test`** — PASS.

- [ ] **Step 3: Commit**

```text
docs: document Bloc 7 validation queue and audit
```

- [ ] **Step 4: Finishing (merge / PR / keep) si demandé.**

---

## Plan self-review

1. **Spec coverage:** V13, AdminProperties, queue GET, validate/reject + flag, audit GET filtrable, pages + nav, IT, README — Tasks 1–8. Pas dashboard/AOP/JWT/`rejected`.
2. **Placeholders:** aucun TBD.
3. **Types:** `ValidationQueueItem` / `AuditEventView` / `RejectResult` cohérents Tasks 3–7.
4. **Filtrage spatial:** validate s’appuie sur filtre `validated` déjà en place (Bloc 6.2) — aucun changement spatial hors effet de statut.
5. **Erreur codes:** `NOT_FOUND`, `PROPERTY_NOT_FOUND`, `ADMIN_REQUEST_INVALID` — à mapper côté `errors.json`.
