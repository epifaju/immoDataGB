# Bloc 6 — Import CSV (thin slice) — Design

Date : 2026-11-14  
Statut : en relecture  
Périmètre : preuve verticale mince (approche A) — Spring Batch in-process, CSV fixe, pas de dédup spatiale, page `/admin/imports`, sans saisie terrain ni Excel  
Dépend de : Cycle 0, Blocs 1–5

Ce bloc **ajoute** les tables `import_job` et `import_row_error`, un job Spring Batch (lecture → validation → skip doublon `reference` → écriture), les endpoints `/api/v1/imports`, le type de source `csv_import`, et la page Next.js `/[locale]/admin/imports`.

Il **n’ajoute pas** de saisie terrain, d’Excel/xlsx, de mapping colonnes configurable, de déduplication pg_trgm/PostGIS, ni de stockage MinIO des fichiers CSV.

## Décisions

- Approche **A** : Batch async in-process, CSV sur disque temp, `MapJobRepository` (ou équivalent in-memory Spring Batch 5), pas de cluster.
- Migration **`V11`** (`V10` = `comparable_observation`). Le numéro `V10` du PRD pour imports est **obsolète**.
- CSV **uniquement** ; en-tête **fixe** (pas de JSON de mapping) :
  `reference,title,property_type,address_text,latitude,longitude`
- Dédup thin : si `reference` existe déjà (`property.deleted_at IS NULL`) → **pas d’insert**, ligne enregistrée dans `import_row_error` avec `IMPORT_DUPLICATE_REFERENCE`.
- Écriture réussie : `property` + `property_geometry` (Point 4326, `geom_type=point`, `precision_level=gps`) + `source` (`source_type=csv_import`).
- Confiance initiale : score **12**, niveau **low**, summary PT déclaratif fixe (aligné seed bas) ; `doc_status=none`.
- `organisation_id` : config `app.imports.organisation-id` (UUID org pilote en `dev` = `00000000-0000-0000-0000-00000000000a`).
- Drapeau **`app.imports.mutations-enabled`** : `false` par défaut, `true` dans `application-dev.yml`. Tant qu’il est faux, **seul** `POST /api/v1/imports` répond **404 `NOT_FOUND`** avant validation (convention documents/confiance). Les GET restent ouverts.
- Miroir UX : `NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED`.
- Accès données métier : **JdbcClient**. Pas de JPA / MapStruct / Bean Validation / Spring Security / JWT.
- Soft-delete : `import_job.deleted_at` ; pas de purge physique des jobs. Fichier temp : delete best-effort en fin de job.
- Dépendance Maven : `spring-boot-starter-batch` (nouvelle).
- Étendre le CHECK `source.source_type` pour inclure **`csv_import`** (absent de V7 ; le PRD le mentionne).
- Hors scope Phase 1 de ce slice : terrain, Excel, JWT, audit_event, Playwright, recalcul confiance post-import, IndexedDB.

## Critère de succès

1. Flyway applique `V11` ; le CHECK `source_type` accepte `csv_import`.
2. Avec `app.imports.mutations-enabled=true` : `POST` d’un CSV valide (en-tête + ≥1 ligne OK) → **202** + `jobId` ; le job passe à `completed` ; un bien apparaît (liste spatiale / fiche) avec géométrie point et source `csv_import`.
3. CSV mélangeant 1 ligne OK, 1 type invalide, 1 `reference` déjà seedée (`BIS-001`) → job `completed` ; `success_rows=1` ; `error_rows=2` ; codes ligne présents dans le report.
4. Drapeau `false` → `POST` 404 `NOT_FOUND` ; `GET …/status` d’un job existant reste 200.
5. Page `/pt/admin/imports` : upload (si flag UI), polling status, compteurs + erreurs ; nav i18n pt/fr/en.
6. `./mvnw verify` et `npm test` passent ; Blocs 0–5 restent verts.

## Hors de ce bloc

Saisie terrain (`/field`), Excel, dédup spatiale/trgm, MinIO pour CSV, mapping configurable, Spring Security/JWT, Bean Validation annotations, `audit_event` (Bloc 7), Playwright, Mode offline, OCR.

## Architecture

```text
POST /api/v1/imports (multipart file)
        ↓ (si mutations-enabled)
ImportController → ImportService.start(file)
  1. Valider extension/MIME/taille/non-vide
  2. Écrire fichier temp
  3. INSERT import_job (status=queued)
  4. Launch Spring Batch Job (async)
        ↓
Job steps:
  Read   → FlatFileItemReader (header fixe)
  Process → validate row ; duplicate reference → skip + error row
  Write  → property + geometry + source
  AfterJob → update compteurs + status completed|failed ; delete temp
        ↓
GET /api/v1/imports/{id}/status
GET /api/v1/imports/{id}/report
GET /api/v1/imports (liste récente pour admin)
```

Packages sous `com.immodata.gb` :

```text
api.imports.ImportController
application.imports.ImportService
application.imports.ImportJobView / ImportReport / ImportRowErrorView
domain.imports.ImportCsvRow (record validé)
infrastructure.batch.imports.ImportBatchConfig
infrastructure.batch.imports.ImportItemProcessor
infrastructure.batch.imports.ImportItemWriter
infrastructure.persistence.imports.ImportRepository
```

Réutiliser : `ApiException`, `ApiResponse`, `PageResponse` si liste paginée, `Clock` UTC, patterns drapeau documents.

## Modèle de données

### `V11__create_import_job.sql` (nom indicatif)

#### `import_job`

| Colonne | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `organisation_id` | UUID NOT NULL | FK `organisation(id)` |
| `status` | VARCHAR(20) NOT NULL | CHECK `queued` \| `processing` \| `completed` \| `failed` |
| `original_filename` | VARCHAR(255) NOT NULL | |
| `stored_path` | TEXT NOT NULL | chemin absolu temp |
| `total_rows` | INT NOT NULL DEFAULT 0 | lignes corps (hors header) |
| `success_rows` | INT NOT NULL DEFAULT 0 | |
| `error_rows` | INT NOT NULL DEFAULT 0 | |
| `error_message` | TEXT NULL | échec global |
| `created_at` / `updated_at` | TIMESTAMPTZ | |
| `created_by` / `updated_by` | UUID NULL | |
| `deleted_at` | TIMESTAMPTZ NULL | |

Index : `(created_at DESC, id)` WHERE `deleted_at IS NULL`.

#### `import_row_error`

| Colonne | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `job_id` | UUID NOT NULL | FK `import_job(id)` ON DELETE CASCADE (logique : jobs soft-deleted restent) |
| `row_number` | INT NOT NULL | 1-based, corps CSV |
| `code` | VARCHAR(80) NOT NULL | code stable |
| `message` | TEXT NOT NULL | message PT API |
| `raw_excerpt` | VARCHAR(500) NULL | tronqué |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `now()` | |

Index : `(job_id, row_number)`.

#### Source type

```sql
ALTER TABLE source DROP CONSTRAINT source_source_type_check;
ALTER TABLE source ADD CONSTRAINT source_source_type_check CHECK (
  source_type IN (
    'agency_listing', 'field_observation', 'scanned_doc', 'institutional',
    'third_party_user', 'partner', 'open_layer', 'csv_import'
  )
);
```

Ne pas modifier V7 ; uniquement via V11.

## Contrat CSV

- Encodage : UTF-8.
- Séparateur : `,`.
- Première ligne = header exact (ordre et noms, case-sensitive) :
  `reference,title,property_type,address_text,latitude,longitude`
- `reference` : non vide, ≤ 50 chars ; unique parmi actifs.
- `title` : non vide, ≤ 500.
- `property_type` : `house` \| `apartment` \| `land` \| `commercial`.
- `address_text` : peut être vide → stocké `NULL`.
- `latitude` / `longitude` : décimaux ; lat ∈ [-90, 90], lng ∈ [-180, 180].

Header incorrect → job peut échouer globalement (`failed` + `error_message`) ou 400 au POST si détecté avant launch — **préférence : validation header au POST** → 400 `IMPORT_INVALID_HEADER` sans créer de job ; validation lignes dans le job.

## API

### Config

```yaml
app:
  imports:
    mutations-enabled: false   # true in application-dev.yml
    organisation-id: 00000000-0000-0000-0000-00000000000a
    max-file-size-bytes: 5242880   # 5 MiB
```

### `POST /api/v1/imports`

- Multipart field name : `file`.
- Réponse **202** :

```json
{
  "data": {
    "jobId": "…",
    "status": "queued"
  }
}
```

### `GET /api/v1/imports/{id}/status`

```json
{
  "data": {
    "jobId": "…",
    "status": "completed",
    "originalFilename": "sample.csv",
    "totalRows": 3,
    "successRows": 1,
    "errorRows": 2,
    "errorMessage": null,
    "createdAt": "…"
  }
}
```

### `GET /api/v1/imports/{id}/report`

Même payload + `errors: [{ "rowNumber", "code", "message", "rawExcerpt" }]`.

### `GET /api/v1/imports`

Liste paginée des jobs récents (`page`, `size`) pour la page admin — `PageResponse`.

### Erreurs HTTP

| Code | HTTP | Quand |
|---|---|---|
| `NOT_FOUND` | 404 | mutations off sur POST |
| `IMPORT_FILE_REQUIRED` | 400 | pas de fichier |
| `IMPORT_FILE_TOO_LARGE` | 400 | > max |
| `IMPORT_INVALID_TYPE` | 400 | pas CSV / MIME |
| `IMPORT_INVALID_HEADER` | 400 | header ≠ contrat |
| `IMPORT_JOB_NOT_FOUND` | 404 | id inconnu / soft-deleted |
| `IMPORT_MISSING_FIELD` | (ligne) | |
| `IMPORT_INVALID_PROPERTY_TYPE` | (ligne) | |
| `IMPORT_INVALID_COORDINATE` | (ligne) | |
| `IMPORT_DUPLICATE_REFERENCE` | (ligne) | |

## Frontend

- `app/[locale]/admin/imports/page.tsx`
- Composants : upload, job status, error table
- `lib/api/imports.ts` + types
- Messages `messages/{pt,fr,en}/imports.json` + `nav.imports` + codes dans `errors.json`
- Polling TanStack Query (`refetchInterval` tant que status ∈ queued|processing)
- Upload visible seulement si `NEXT_PUBLIC_IMPORTS_MUTATIONS_ENABLED === 'true'`

## Tests

- Unit : parse/validate header + row.
- WebMvc : POST flag on/off ; GET status/report.
- IT : Testcontainers PostGIS ; CSV fixture 3 lignes ; assert property + geom + source + 2 row errors.
- Vitest : form + report rendering ; messages parity.
- README : curl POST + poll status + flag env.

## README

Documenter : drapeau backend/frontend, colonnes CSV, limites 5 Mo, exemple curl, page `/admin/imports`, comportement doublon `reference`.

## Checklist

- [ ] V11 import_job + import_row_error + source_type csv_import
- [ ] Spring Batch job + temp file
- [ ] POST/GET imports + drapeau mutations
- [ ] Page admin imports + nav + i18n
- [ ] IT 3-lignes + WebMvc + Vitest
- [ ] README
- [ ] Pas de terrain / Excel / JWT
