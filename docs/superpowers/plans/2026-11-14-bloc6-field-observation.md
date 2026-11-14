# Bloc 6.2 — Saisie terrain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Créer une observation terrain (`pending_validation`) via JSON + page `/field/new` GPS ; exclure les pending de la carte jusqu’au Bloc 7.

**Architecture:** `V12` ajoute `property.validation_status` (DEFAULT `validated`). `POST /api/v1/field-observations` derrière drapeau crée property + geom + source `field_observation`. Liste/carte filtrée `validated` ; GET détail autorise pending.

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, JdbcClient, PostGIS, Next.js 14.2, TanStack Query, next-intl, Vitest, Testcontainers PostGIS.

**Spec:** `docs/superpowers/specs/2026-11-14-bloc6-field-observation-design.md`.

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, photos, localStorage draft, endpoint approve, audit_event, Playwright, colonnes prix/surface.
- JdbcClient uniquement.
- `POST /api/v1/field-observations` derrière **`app.field.mutations-enabled`** (404 `NOT_FOUND` si off).
- Création → toujours `pending_validation` ; seed/imports restent `validated` via DEFAULT.
- Liste/carte spatiale : `validation_status = 'validated'` ; `findById` sans ce filtre.
- Référence `FLD-{yyyyMMdd}-{8 hex}` UTC `Clock` ; confiance 12/low ; summary PT fixe.
- Shell PowerShell ; commits `git.exe commit -F` UTF-8 **sans BOM**. Ne pas modifier `V1`–`V11`.

---

## File map

**Créer :**
- `backend/src/main/resources/db/migration/V12__add_property_validation_status.sql`
- `infrastructure/config/FieldProperties.java`
- `application/field/FieldObservationRequest.java`, `FieldObservationCreated.java`, `FieldObservationRequestParser.java`, `FieldObservationService.java`
- `infrastructure/persistence/field/FieldObservationRepository.java`
- `api/field/FieldObservationController.java`
- tests unit / WebMvc / IT
- `frontend/app/[locale]/field/new/page.tsx`
- `frontend/components/field/FieldObservationForm.tsx` (+ test)
- `frontend/lib/api/field.ts`
- `frontend/messages/{pt,fr,en}/field.json`

**Modifier :**
- `application.yml`, `application-dev.yml`
- `PropertySpatialRepository.java` (filtre validated sur list/map)
- `frontend/lib/api/types.ts`
- `frontend/messages/{pt,fr,en}/common.json`, `errors.json`
- `frontend/i18n/request.ts`, `layout.tsx`, `messages.test.ts`
- `frontend/.env.example`
- `README.md`
- Vérifier `ImportRepository.insertPropertyBundle` : laisser DEFAULT DB (pas besoin d’écrire la colonne si INSERT n’ommet pas de façon ambiguë)

---

### Task 1: `FieldProperties` + YAML

**Files:**
- Create: `FieldProperties.java`
- Modify: `application.yml`, `application-dev.yml`
- Test: `FieldPropertiesTest`

**Interfaces:**
```java
@ConfigurationProperties("app.field")
public record FieldProperties(
    @DefaultValue("false") boolean mutationsEnabled,
    UUID organisationId
) {}
```
- Dev : mutations true, org `00000000-0000-0000-0000-00000000000a`
- Scan via `@ConfigurationPropertiesScan` existant

- [ ] **Step 1: Properties + YAML + test.**

- [ ] **Step 2: Commit**

```text
feat: add field observation properties
```

---

### Task 2: Migration V12

**Files:**
- Create: `V12__add_property_validation_status.sql`

**Interfaces:**
- SQL exact spec § Modèle (DEFAULT `validated`, CHECK, index partiel).

- [ ] **Step 1: Écrire V12.**

- [ ] **Step 2: Commit**

```text
feat: add property validation_status column
```

---

### Task 3: Filtre spatial `validated`

**Files:**
- Modify: `PropertySpatialRepository.java`
- Test: étendre IT spatiale existante **ou** couvrir dans Task 7 — **ici** ajouter assert minimal si IT spatiale existante facile ; sinon commenter dans report que Task 7 couvre pending-absent.

**Interfaces:**
- Toutes les requêtes list/map/count : `AND p.validation_status = 'validated'`
- `findById` / détail : **inchangé** (pas de filtre validation)

- [ ] **Step 1: Modifier SQL list/map.**

- [ ] **Step 2: Commit**

```text
fix: hide pending_validation properties from spatial list
```

---

### Task 4: Parser + repository + service (TDD)

**Files:**
- Create: request/created/parser/service/repository
- Test: `FieldObservationRequestParserTest`, `FieldObservationServiceTest`

**Interfaces:**
```java
record FieldObservationRequest(
  double latitude, double longitude, String propertyType,
  String title, String occupationStatus, String notes)

record FieldObservationCreated(
  UUID id, String reference, String title, String propertyType,
  String validationStatus, double latitude, double longitude,
  int confidenceScore, String confidenceLevel)

FieldObservationCreated FieldObservationService.create(FieldObservationRequest req)
// flag off → NOT_FOUND; org null → FIELD_ORGANISATION_MISSING
// reference FLD-{yyyyMMdd}-{8 hex}; title default PT; label from occupation+notes ≤200
```
- Repo `@Transactional` insert property + geometry + source.
- Confidence 12/low, summary `"Fonte declarativa única; verificação pendente."`, `doc_status=none`, `validation_status=pending_validation`.

- [ ] **Step 1: Tests parser + service (TDD).**

- [ ] **Step 2: Implémenter.**

- [ ] **Step 3: Commit**

```text
feat: create pending field observations
```

---

### Task 5: Controller + WebMvcTest

**Files:**
- Create: `FieldObservationController.java`, `FieldObservationControllerTest.java`

**Interfaces:**
```text
POST /api/v1/field-observations → 201 ApiResponse<FieldObservationCreated>
```
- Flag off 404 ; body invalid 400 `FIELD_REQUEST_INVALID`

- [ ] **Step 1: WebMvc TDD + controller.**

- [ ] **Step 2: Commit**

```text
feat: expose field observation create endpoint
```

---

### Task 6: IT PostGIS

**Files:**
- Create: `FieldObservationIT.java`, éventuellement `FieldMutationsDisabledIT.java`

**Interfaces:**
- Mutations on : POST → 201 pending ; spatial list ne contient pas l’id ; GET `/api/v1/properties/{id}` → 200
- Seed `BIS-001` toujours présent en liste spatiale
- Mutations off → POST 404
- `.\mvnw.cmd verify` — BUILD SUCCESS

- [ ] **Step 1: IT + verify.**

- [ ] **Step 2: Commit**

```text
test: cover field observation pending vs spatial list
```

---

### Task 7: Frontend `/field/new`

**Files:**
- Create: page, form, `lib/api/field.ts`, `messages/*/field.json`
- Modify: types, common/errors, i18n, layout, messages.test, `.env.example`
- Test: Vitest form (flag, submit mock, geolocation mock)

**Interfaces:**
- `nav.field` : pt `Terreno` / fr `Terrain` / en `Field`
- Submit si `NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED=true`
- Succès : référence + lien fiche
- `npm test` PASS

- [ ] **Step 1: UI + i18n + tests.**

- [ ] **Step 2: Commit**

```text
feat: add field observation form page
```

---

### Task 8: README + vérif finale

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Documenter curl, flags, page `/field/new`, pending hors carte.**

- [ ] **Step 2: `.\mvnw.cmd verify` + `npm test`** — PASS.

- [ ] **Step 3: Commit**

```text
docs: document Bloc 6.2 field observation
```

- [ ] **Step 4: Finishing (merge / PR / keep) si demandé.**

---

## Plan self-review

1. **Spec coverage:** V12, FieldProperties, filtre spatial, POST create, IT pending vs map, page field, README — Tasks 1–8. Pas photos/approve.
2. **Placeholders:** aucun TBD.
3. **Types:** `FieldObservationRequest` / `Created` cohérents Tasks 4–7.
4. **Imports CSV:** DEFAULT `validated` ; Task 4/2 documente si INSERT Import doit lister la colonne.
