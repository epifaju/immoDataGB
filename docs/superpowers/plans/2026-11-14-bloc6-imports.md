# Bloc 6 — Import CSV (thin slice) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import CSV async via Spring Batch : `POST /imports` → job + rapport d’erreurs ligne à ligne ; insert `property` + geom + `source(csv_import)` ; page `/admin/imports`.

**Architecture:** `V11` crée `import_job` / `import_row_error` et étend `source_type` avec `csv_import`. Fichier temp local + Batch in-process (`MapJobRepository`). Drapeau `app.imports.mutations-enabled` sur le POST. Dédup = skip si `reference` active. Frontend upload + polling + rapport.

**Tech Stack:** Java 21, Spring Boot 3.3.13, `spring-boot-starter-batch`, Flyway, JdbcClient, PostGIS, Next.js 14.2, TanStack Query, next-intl, Vitest, Testcontainers PostGIS.

**Spec:** `docs/superpowers/specs/2026-11-14-bloc6-imports-design.md`. Recopier colonnes CSV, codes erreur, JSON status/report — ne pas inventer.

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, Excel, saisie terrain, MinIO pour CSV, mapping configurable, Playwright, dédup pg_trgm/PostGIS.
- JdbcClient pour persistance métier ; Batch in-memory job meta (`MapJobRepository` + `ResourcelessTransactionManager`).
- `POST /api/v1/imports` derrière **`app.imports.mutations-enabled`** (404 `NOT_FOUND` si off). GET status/report/list toujours ouverts.
- Header CSV invalid → **400 `IMPORT_INVALID_HEADER` au POST**, aucun job créé.
- Doublon `reference` (actif) → `import_row_error` `IMPORT_DUPLICATE_REFERENCE`, pas d’insert.
- Confiance initiale : score **12**, level **low**, summary PT déclaratif fixe ; `doc_status=none`.
- Shell PowerShell ; commits via `git.exe commit -F` (UTF-8 **sans BOM**). Ne pas modifier `V1`–`V10`.

---

## File map

**Créer :**
- `backend/src/main/resources/db/migration/V11__create_import_job.sql`
- `infrastructure/config/ImportProperties.java`
- `domain/imports/ImportCsvRow.java` (record)
- `application/imports/ImportCsvValidator.java` (+ header check)
- `application/imports/ImportService.java`
- `application/imports/ImportJobStatus.java`, `ImportReport.java`, `ImportRowErrorItem.java`, `ImportStartResult.java`
- `infrastructure/persistence/imports/ImportRepository.java`
- `infrastructure/batch/imports/ImportBatchConfig.java`
- `infrastructure/batch/imports/ImportItemProcessor.java`
- `infrastructure/batch/imports/ImportItemWriter.java`
- `infrastructure/batch/imports/ImportJobListener.java` (compteurs + status + delete temp)
- `api/imports/ImportController.java`
- tests unit / WebMvc / IT
- `frontend/app/[locale]/admin/imports/page.tsx`
- `frontend/components/imports/ImportUploadForm.tsx`, `ImportJobPanel.tsx` (+ tests)
- `frontend/lib/api/imports.ts`
- `frontend/messages/{pt,fr,en}/imports.json`

**Modifier :**
- `backend/pom.xml` (`spring-boot-starter-batch`)
- `backend/src/main/resources/application.yml` + `application-dev.yml`
- `frontend/lib/api/types.ts`
- `frontend/messages/{pt,fr,en}/common.json` (`nav.imports`)
- `frontend/messages/{pt,fr,en}/errors.json`
- `frontend/i18n/request.ts`
- `frontend/app/[locale]/layout.tsx`
- `frontend/messages/messages.test.ts`
- `frontend/.env.example` (`NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED`)
- `README.md`

---

### Task 1: Dépendance Batch + `ImportProperties` + YAML

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/immodata/gb/infrastructure/config/ImportProperties.java`
- Modify: `application.yml`, `application-dev.yml`
- Test: `ImportPropertiesTest` (mutations default false ; binding)

**Interfaces:**
```java
@ConfigurationProperties("app.imports")
public record ImportProperties(
    @DefaultValue("false") boolean mutationsEnabled,
    UUID organisationId,
    @DefaultValue("5242880") long maxFileSizeBytes
) {}
```
- `application.yml` : `mutations-enabled: false`, `organisation-id` + `max-file-size-bytes: 5242880`
- `application-dev.yml` : `mutations-enabled: true`, org = `00000000-0000-0000-0000-00000000000a`
- Activer `@EnableConfigurationProperties(ImportProperties.class)` comme documents.

- [ ] **Step 1: Ajouter dépendance + properties + YAML + test binding.**

- [ ] **Step 2: `.\mvnw.cmd "-Dtest=ImportPropertiesTest"`** — PASS.

- [ ] **Step 3: Commit**

```text
feat: add import properties and Spring Batch dependency
```

---

### Task 2: Migration V11

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__create_import_job.sql`

**Interfaces:**
- Tables exactes spec § Modèle (`import_job`, `import_row_error`).
- ALTER CHECK `source_source_type_check` pour ajouter `csv_import`.
- Index job actifs + `(job_id, row_number)`.

- [ ] **Step 1: Écrire V11** selon spec.

- [ ] **Step 2: Commit**

```text
feat: add import job schema and csv_import source type
```

---

### Task 3: Validateur CSV (TDD)

**Files:**
- Create: `domain/imports/ImportCsvRow.java`, `application/imports/ImportCsvValidator.java`
- Test: `ImportCsvValidatorTest`

**Interfaces:**
```java
public record ImportCsvRow(
    int rowNumber,
    String reference,
    String title,
    String propertyType,
    String addressText,   // null si vide
    double latitude,
    double longitude
) {}

// ImportCsvValidator:
void assertValidHeader(String[] header)           // throws ApiException IMPORT_INVALID_HEADER
ImportCsvRow parseAndValidate(int rowNumber, String[] cols)
// parseAndValidate lève ImportRowException(code, message) pour erreurs ligne
// Header exact: reference,title,property_type,address_text,latitude,longitude
```
- Codes ligne : `IMPORT_MISSING_FIELD`, `IMPORT_INVALID_PROPERTY_TYPE`, `IMPORT_INVALID_COORDINATE` (+ message PT).
- `reference` ≤ 50, `title` ≤ 500, types enum property, lat/lng bornes.

- [ ] **Step 1: Tests RED** (header OK/KO, ligne OK, type invalide, lat hors borne, champs manquants).

- [ ] **Step 2: Implémenter validateur.**

- [ ] **Step 3: Commit**

```text
feat: validate fixed CSV import header and rows
```

---

### Task 4: `ImportRepository` + vues

**Files:**
- Create: `ImportRepository.java`, records `ImportJobStatus`, `ImportReport`, `ImportRowErrorItem`, `ImportStartResult`
- Test: pas d’IT dédié ici — couverture repo via `ImportIT` (Task 7).

**Interfaces:**
```java
UUID insertJob(UUID orgId, String originalFilename, String storedPath)
void markProcessing(UUID jobId)
void markFinished(UUID jobId, String status, int total, int success, int errors, String errorMessage)
void insertRowError(UUID jobId, int rowNumber, String code, String message, String rawExcerpt)
boolean existsActiveReference(String reference)
void insertPropertyBundle(UUID orgId, ImportCsvRow row)  // property + geometry + source csv_import + confidence 12/low
Optional<ImportJobStatus> findStatus(UUID jobId)
ImportReport findReport(UUID jobId)
PageResponse-like list or List<ImportJobStatus> findRecent(int limit) // pour GET list — utiliser PageResponse existant si page/size
```
- Insert property : `confidence_score=12`, `confidence_level=low`, summary PT fixe `"Fonte declarativa única; verificação pendente."`, `doc_status=none`.
- Geometry : `ST_SetSRID(ST_MakePoint(lng, lat), 4326)`, `geom_type=point`, `precision_level=gps`.
- Source : `source_type=csv_import`, label optionnel = filename ou null.

- [ ] **Step 1: Implémenter repo + records.**

- [ ] **Step 2: Commit**

```text
feat: persist import jobs and property bundles from CSV rows
```

---

### Task 5: Spring Batch job + `ImportService`

**Files:**
- Create: `ImportBatchConfig`, `ImportItemProcessor`, `ImportItemWriter`, `ImportJobListener`, `ImportService`
- Test: `ImportServiceTest` (mock repo + JobLauncher ; flag off ; header invalid ; happy path launch)

**Interfaces:**
```java
ImportStartResult ImportService.start(MultipartFile file)
// 1) if !mutationsEnabled → ApiException NOT_FOUND
// 2) validate file present, size ≤ max, name/content type csv
// 3) read first line → assertValidHeader
// 4) copy to temp file
// 5) insertJob(queued) ; launch job with JobParameters jobId + filePath
// 6) return ImportStartResult(jobId, "queued")

ImportJobStatus getStatus(UUID id)  // NOT_FOUND → IMPORT_JOB_NOT_FOUND
ImportReport getReport(UUID id)
PageResponse<ImportJobStatus> list(int page, int size)
```
- Batch : `FlatFileItemReader` skip header ; processor appelle validator + `existsActiveReference` → skip + `insertRowError` ; writer `insertPropertyBundle`.
- Listener `afterJob` : compteurs depuis repo (ou ExecutionContext), `completed` si finished sans crash, `failed` + `error_message` si exception ; delete temp best-effort.
- Au démarrage step : `markProcessing`.
- `@EnableBatchProcessing` **uniquement** si nécessaire ; config explicite `MapJobRepository` pour ne pas exiger tables BATCH_*.

- [ ] **Step 1: Tests service (TDD) flag / header / launch.**

- [ ] **Step 2: Implémenter Batch + service.**

- [ ] **Step 3: Commit**

```text
feat: run CSV import jobs with Spring Batch
```

---

### Task 6: Controller + WebMvcTest

**Files:**
- Create: `ImportController.java`
- Test: `ImportControllerTest.java`

**Interfaces:**
```text
POST /api/v1/imports                 → 202 ApiResponse<ImportStartResult>
GET  /api/v1/imports/{id}/status     → 200 ApiResponse<ImportJobStatus>
GET  /api/v1/imports/{id}/report     → 200 ApiResponse<ImportReport>
GET  /api/v1/imports?page&size       → 200 PageResponse (ou ApiResponse wrapper existant — suivre pattern liste projet)
```
- WebMvc : mutations off → POST 404 ; file missing → 400 ; status/report 200 avec mock service.

- [ ] **Step 1: Tests WebMvc (TDD).**

- [ ] **Step 2: Implémenter controller.**

- [ ] **Step 3: Commit**

```text
feat: expose CSV import API endpoints
```

---

### Task 7: IT PostGIS

**Files:**
- Create: `ImportIT.java`
- Fixture classpath : `src/test/resources/imports/sample-mixed.csv`

**Interfaces:**
- Profil `dev`, mutations on, Testcontainers PostGIS.
- CSV 3 lignes corps :
  1. référence neuve `IMP-IT-001`, apartment, coords Bissau → success
  2. `property_type=castle` → `IMPORT_INVALID_PROPERTY_TYPE`
  3. `reference=BIS-001` → `IMPORT_DUPLICATE_REFERENCE`
- Attendre job `completed` (poll status ≤ ~10 s).
- Assert : `success_rows=1`, `error_rows=2` ; property `IMP-IT-001` + geom + source `csv_import`.
- Header invalide au POST → 400 sans job.
- Mutations false (test dédié ou `@TestPropertySource`) → POST 404.
- `.\mvnw.cmd verify` — BUILD SUCCESS.

- [ ] **Step 1: Écrire IT + fixture.**

- [ ] **Step 2: `.\mvnw.cmd verify`** — PASS.

- [ ] **Step 3: Commit**

```text
test: cover CSV import job against PostGIS
```

---

### Task 8: Frontend `/admin/imports`

**Files:**
- Create: page, `ImportUploadForm`, `ImportJobPanel`, `lib/api/imports.ts`, `messages/*/imports.json`
- Modify: `types.ts`, `common.json`, `errors.json`, `i18n/request.ts`, `layout.tsx`, `messages.test.ts`, `.env.example`
- Test: Vitest upload visibility + report table

**Interfaces:**
- `nav.imports` : pt `Importações` / fr `Imports` / en `Imports`
- Upload si `NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED=true`
- Après POST : poll `getImportStatus` / afficher `getImportReport` quand terminal
- Afficher compteurs + liste erreurs (code traduit)
- Pas de MapLibre

- [ ] **Step 1: i18n + API + composants + page + nav.**

- [ ] **Step 2: `npm test`** — PASS.

- [ ] **Step 3: Commit**

```text
feat: add admin imports page with CSV upload
```

---

### Task 9: README + vérification finale

**Files:**
- Modify: `README.md` (curl POST imports, poll status, colonnes CSV, flags, page `/admin/imports`, doublon reference)

- [ ] **Step 1: README.**

- [ ] **Step 2: `.\mvnw.cmd verify` + `npm test`** — PASS.

- [ ] **Step 3: Commit**

```text
docs: document Bloc 6 CSV import
```

- [ ] **Step 4: Finishing (merge / PR / keep) si demandé.**

---

## Plan self-review

1. **Spec coverage:** V11 + csv_import, Batch temp, drapeau POST, header 400, dédup reference, property+geom+source, confiance 12/low, status/report/list, page admin, IT 3 lignes, README — Tasks 1–9. Pas de terrain/Excel/JWT.
2. **Placeholders:** aucun TBD ; couverture repo via Task 7.
3. **Types:** `ImportCsvRow` / `ImportJobStatus` / `ImportReport` / `ImportStartResult` cohérents Tasks 3–8.
4. **BATCH_ meta tables:** Task 5 impose `MapJobRepository` pour éviter migration Spring Batch JDBC.
