# Bloc 7 — Validation queue + audit (thin slice) — Design

Date : 2026-11-14  
Statut : approuvé  
Périmètre : preuve verticale mince (approche A) — file pending, validate/reject, `audit_event`, pages admin ; pas de dashboard ni AOP ni JWT  
Dépend de : Cycle 0, Blocs 1–6.2

Ce bloc **ajoute** la table `audit_event`, les endpoints admin de file / validate / reject / audit, et les pages `/admin/validation-queue` et `/admin/audit`.

Il **n’ajoute pas** de dashboard KPIs, d’aspect AOP, de Spring Security/JWT, ni de statut `rejected` (reject = soft-delete).

## Décisions

- Approche **A** : API sous `/api/v1/admin/…` + écriture audit explicite dans le service.
- Migration **`V13`** (`V12` = `validation_status`).
- **Validate** : `validation_status` → `validated` ; le bien devient visible carte/liste.
- **Reject** : soft-delete (`deleted_at = now()`) ; plus visible (liste/carte/détail soft-deleted).
- Drapeau **`app.admin.mutations-enabled`** : `false` par défaut, `true` en `dev`. Seuls **POST** validate/reject → 404 `NOT_FOUND` si off. GET queue + audit restent ouverts.
- Miroir UX : `NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED`.
- Audit uniquement sur validate/reject (`action` ∈ `validate` \| `reject`). Pas d’AOP.
- `performed_by` NULL (pas de JWT) ; `ip_address` NULL dans ce slice.
- Accès données : **JdbcClient**. Pas de JPA / MapStruct / Bean Validation / Security.
- Soft-delete inchangé sur `property`.
- Hors scope : dashboard, AOP, JWT, users, Playwright, Bloc 8.

## Critère de succès

1. Flyway applique `V13` ; table `audit_event` créée.
2. Avec mutations on : un bien `pending_validation` apparaît dans `GET …/validation-queue` ; `POST …/validate` → status `validated` + ligne audit ; le bien apparaît ensuite en liste spatiale.
3. `POST …/reject` sur un autre pending → soft-deleted + audit ; absent de queue et de carte ; GET détail 404.
4. Drapeau off → POST validate/reject 404 ; GET queue/audit 200.
5. Pages `/pt/admin/validation-queue` et `/pt/admin/audit` + nav i18n.
6. `./mvnw verify` et `npm test` passent ; Blocs 0–6 restent verts.

## Hors de ce bloc

Dashboard admin, AOP audit transversale, JWT/RBAC, `performed_by` réel, ip capture, Playwright, file imports (déjà Bloc 6.1), profil (Bloc 8).

## Architecture

```text
GET  /api/v1/admin/validation-queue
POST /api/v1/admin/properties/{id}/validate  } drapeau mutations
POST /api/v1/admin/properties/{id}/reject    }
GET  /api/v1/admin/audit

AdminValidationService
  validate(id) → load pending → set validated → insert audit
  reject(id)   → load pending → soft-delete → insert audit

AdminAuditRepository / AdminValidationRepository (JdbcClient)
```

Packages :

```text
api.admin.AdminValidationController
api.admin.AdminAuditController
application.admin.AdminValidationService
application.admin.ValidationQueueItem
application.admin.AuditEventView
infrastructure.config.AdminProperties
infrastructure.persistence.admin.AdminValidationRepository
infrastructure.persistence.admin.AuditEventRepository
```

## Modèle de données

### `V13__create_audit_event.sql`

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

Thin slice : CHECK limité à `validate`/`reject`. Extension future = nouvelle migration (ne pas modifier V13).

## API

### Config

```yaml
app:
  admin:
    mutations-enabled: false   # true in application-dev.yml
```

### `GET /api/v1/admin/validation-queue`

Query : `page` (0), `size` (20, max 100).  
Filtre : `property.deleted_at IS NULL AND validation_status = 'pending_validation'`.  
Réponse : `PageResponse<ValidationQueueItem>` (`id`, `reference`, `title`, `propertyType`, `createdAt`, `latitude`, `longitude`).

### `POST /api/v1/admin/properties/{id}/validate`

- Mutations off → 404 `NOT_FOUND`
- Bien absent / soft-deleted / pas pending → 404 `PROPERTY_NOT_FOUND`
- Succès → 200 `ApiResponse` avec détail minimal + audit insert :
  - `old_value` : `{"validationStatus":"pending_validation"}`
  - `new_value` : `{"validationStatus":"validated"}`
  - `summary` PT : `Bem validado ({reference}).`

### `POST /api/v1/admin/properties/{id}/reject`

- Mêmes gardes drapeau / état
- Soft-delete + audit :
  - `old_value` : `{"validationStatus":"pending_validation","deleted":false}`
  - `new_value` : `{"deleted":true}`
  - `summary` PT : `Bem rejeitado ({reference}).`
- Réponse **200** `ApiResponse` avec `{ "id", "deleted": true }`.

### `GET /api/v1/admin/audit`

Query optionnels : `entityType`, `action`, `from`, `to` (ISO-8601 instant), `page`, `size`.  
Réponse : `PageResponse<AuditEventView>`.

### Erreurs

| Code | HTTP |
|---|---|
| `NOT_FOUND` | 404 | mutations off sur POST |
| `PROPERTY_NOT_FOUND` | 404 | id invalide / pas pending |
| `ADMIN_REQUEST_INVALID` | 400 | query audit/page invalides |

## Frontend

- `app/[locale]/admin/validation-queue/page.tsx`
- `app/[locale]/admin/audit/page.tsx`
- Composants liste + actions ; filtres audit
- `lib/api/admin.ts` + types
- Messages `admin.json` ; `nav.validationQueue` / `nav.audit` (pt/fr/en)
- Actions visibles si `NEXT_PUBLIC_ADMIN_MUTATIONS_ENABLED === 'true'`
- Après succès : invalider queries queue (+ audit)

## Tests

- Service unit : validate/reject guards + audit payload
- WebMvc : flag, validate, reject, audit list filters
- IT PostGIS : field create → queue → validate → spatial visible ; reject path + audit rows
- Vitest : queue buttons + audit table
- README : curl validate/reject, flags, pages

## Checklist

- [ ] V13 audit_event
- [ ] Queue GET + validate/reject POST + drapeau
- [ ] Audit GET filtrable
- [ ] Pages validation-queue + audit + nav
- [ ] IT + WebMvc + Vitest
- [ ] README
- [ ] Pas de dashboard / AOP / JWT
