# Bloc 4 — Gestion documentaire Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Documents liés à un bien : upload multipart validé (Tika) → MinIO, liste métadonnées, URL signée 15 min, soft-delete, section fiche, mutations derrière drapeau.

**Architecture:** `V9` crée `document`. `DocumentService` orchestre validation → INSERT → `DocumentStorage.put`. Mutations protégées par `app.documents.mutations-enabled` (404 `NOT_FOUND` si faux). UI fiche + `NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED`. CORS étendu POST/DELETE. IT PostGIS avec `DocumentStorage` stub en mémoire ; adapter S3 testé unitairement (client mocké).

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, JdbcClient, AWS SDK v2 `s3` (endpoint MinIO path-style), Apache Tika, Next.js 14.2, next-intl, Vitest, Testcontainers PostGIS.

**Spec:** `docs/superpowers/specs/2026-11-13-bloc4-documents-design.md`. Recopier CHECK SQL, MIME table, codes d’erreur et formes JSON — ne pas inventer.

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, audit UI, OCR, virus scan, SSE-S3, Playwright, seed de binaires, recalcul confiance à l’upload, `document_entity_link` / `media`.
- JdbcClient uniquement ; nouvelles deps Maven : `software.amazon.awssdk:s3` (+ BOM SDK si besoin) et `org.apache.tika:tika-core` (version compatible Java 21 / Boot 3.3).
- Drapeau `app.documents.mutations-enabled` : **false** dans `application.yml`, **true** dans `application-dev.yml`. Si faux → 404 `NOT_FOUND` **avant** validation d’id sur POST upload, POST signed-url, DELETE.
- Soft-delete : `deleted_at` seulement ; **jamais** `DeleteObject` MinIO.
- Bien : même règle Bloc 2 (actif + géométrie) pour list/upload.
- Réutiliser `UuidParam`, `ApiException`, `Clock` UTC, `@ConfigurationPropertiesScan`.
- Shell PowerShell ; commits via `git.exe commit -F` (fichier UTF-8 **sans BOM**). Ne pas modifier `V1`–`V8`.

---

## File map

**Créer :**
- `backend/src/main/resources/db/migration/V9__create_document.sql`
- `domain/document/DocumentType.java`
- `application/document/DocumentItem.java`, `SignedUrlResult.java`, `DocumentService.java`, `DocumentFileValidator.java`, `StorageKeyFactory.java`
- `infrastructure/persistence/document/DocumentRepository.java`
- `infrastructure/storage/DocumentStorage.java`, `S3DocumentStorage.java`, `StorageProperties.java` (ou fusion dans DocumentProperties)
- `infrastructure/config/DocumentProperties.java`, `S3ClientConfig.java`
- `api/document/DocumentController.java`
- tests unit / WebMvc / IT associés
- `frontend/lib/api/documents.ts`
- `frontend/components/property/DocumentList.tsx` (+ test), `DocumentUpload.tsx` (+ test)

**Modifier :**
- `backend/pom.xml`
- `application.yml`, `application-dev.yml`
- `CorsConfig.java`
- `.env.example`, `frontend/.env.example`
- `frontend/lib/api/types.ts`
- `PropertyDetailView.tsx` (+ test si besoin)
- `messages/{pt,fr,en}/property.json`, `messages/{pt,fr,en}/errors.json`
- `README.md`

---

### Task 1: Migration V9

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__create_document.sql`

**Interfaces:**
- Produces: table `document` exacte spec § Modèle (CHECK type 10 valeurs, size ≤ 52428800, filename btrim, UNIQUE `storage_key`, index actifs).

- [ ] **Step 1: Écrire V9** — SQL verbatim des colonnes / contraintes / index de la spec.

- [ ] **Step 2: Commit**

```text
feat: add document table schema
```

---

### Task 2: Domaine, validation fichier, config, dépendances

**Files:**
- Create: `DocumentType`, `DocumentFileValidator`, `StorageKeyFactory`
- Create: `DocumentProperties` (`mutationsEnabled`, `bucket`, `signedUrlTtl` Duration)
- Create: `StorageProperties` (`endpoint`, `accessKey`, `secretKey`, `region`) sous `app.storage`
- Modify: `pom.xml` (AWS SDK v2 s3 + tika-core)
- Modify: `application.yml` / `application-dev.yml` (multipart 50MB, documents flag, storage dev)
- Test: `DocumentTypeTest`, `DocumentFileValidatorTest`, `StorageKeyFactoryTest`, `DocumentPropertiesTest`

**Interfaces:**
- `DocumentType` enum + `fromApi(String) → Optional` / throw via service ; `value()` = littéral SQL.
- `DocumentFileValidator.validate(MultipartFile file) → ValidatedFile(originalFilename, extension, contentType, sizeBytes, InputStream/bytes)`  
  Ordre et codes : `DOCUMENT_FILE_MISSING` (null/empty/0), `DOCUMENT_FILE_TOO_LARGE` (> 52428800), `DOCUMENT_FILE_TYPE_REJECTED` (extension ou MIME hors table spec).
- `StorageKeyFactory.build(UUID orgId, UUID propertyId, UUID documentId, String originalFilename, String validatedExtension) → String`
  Format `{org}/{property}/{doc}/{safeStem}.{ext}` ; stem : hors `[A-Za-z0-9._-]` → `_` ; stem max 100 chars.
- MIME table : recopier spec § Validation upload.
- `DocumentProperties` : `@ConfigurationProperties("app.documents")`, défauts `mutationsEnabled=false`, `bucket=documents`, `signedUrlTtl=15m`.

- [ ] **Step 1: Tests unitaires** DocumentType (10 ok + inconnu), validator (pdf ok, spoof texte→pdf rejeté, taille 0 / >50Mo, heic/docx mapping), StorageKeyFactory (espace → `_`, extension forcée lowercase whitelist).

- [ ] **Step 2: Implémenter + pom + yml.**

- [ ] **Step 3: `.\mvnw.cmd "-Dtest=DocumentTypeTest,DocumentFileValidatorTest,StorageKeyFactoryTest,DocumentPropertiesTest"` test** — PASS.

- [ ] **Step 4: Commit**

```text
feat: add document type validation and storage key factory
```

---

### Task 3: `DocumentStorage` + S3 + stub

**Files:**
- Create: `DocumentStorage` interface
- Create: `S3DocumentStorage` + `S3ClientConfig` (`@Bean S3Client` path-style vers `app.storage.endpoint`)
- Create: `InMemoryDocumentStorage` dans `src/test/java` (ou `main` profile test only — préférer test)
- Test: `S3DocumentStorageTest` (S3Client mockito : put / presign)

**Interfaces:**
```java
public interface DocumentStorage {
  void put(String bucket, String key, InputStream data, long sizeBytes, String contentType);
  URI presignGet(String bucket, String key, Duration ttl);
}
```
- `put` : échec SDK → laisser remonter ; service mappe en `DOCUMENT_STORAGE_UNAVAILABLE`.
- `presignGet` : URL GET, expiration = ttl (15m).
- Config path-style obligatoire pour MinIO local (`forcePathStyle(true)`).
- Bean `S3DocumentStorage` actif hors tests IT qui fournissent un stub `@Primary` / `@TestConfiguration`.

- [ ] **Step 1: Interface + tests mock S3Client (put appelé avec bons args ; presign non null).**

- [ ] **Step 2: Implémenter S3DocumentStorage + S3ClientConfig + InMemoryDocumentStorage (Map clé → bytes) pour IT.**

- [ ] **Step 3: Commit**

```text
feat: add MinIO S3 document storage adapter
```

---

### Task 4: `DocumentRepository` + `DocumentItem`

**Files:**
- Create: `DocumentItem` record (champs API camelCase Jackson)
- Create: `SignedUrlResult` record (`String url`, `Instant expiresAt`)
- Create: `DocumentRepository`
- Record interne éventuel `DocumentRow` (id, org, property, type, filename, contentType, size, bucket, key, createdAt) pour signed-url / delete

**Interfaces:**
- `DocumentItem(UUID id, UUID propertyId, String documentType, String originalFilename, String contentType, long sizeBytes, Instant createdAt)`
- `boolean propertyExistsActiveWithGeometry(UUID propertyId)`
- `Optional<UUID> findOrganisationIdForActiveProperty(UUID propertyId)` — empty si pas actif/géométrie
- `List<DocumentItem> listActiveByProperty(UUID propertyId)` — `ORDER BY created_at DESC, id`
- `void insert(...tous champs persistés y compris storage_key, bucket...)` — id fourni par le service (`UUID.randomUUID()`)
- `Optional<DocumentRow> findActiveById(UUID id)`
- `boolean softDelete(UUID id)` — true si 1 ligne mise à jour (`deleted_at IS NULL`)

SQL list :
```sql
SELECT d.id, d.property_id, d.document_type, d.original_filename, d.content_type, d.size_bytes, d.created_at
FROM document d
WHERE d.property_id = :propertyId AND d.deleted_at IS NULL
ORDER BY d.created_at DESC, d.id
```

Existence bien (comme Bloc 2) :
```sql
SELECT EXISTS (
  SELECT 1 FROM property p
  JOIN property_geometry g ON g.property_id = p.id
  WHERE p.id = :id AND p.deleted_at IS NULL
)
```

- [ ] **Step 1: Implémenter records + repository.**

- [ ] **Step 2: Commit**

```text
feat: add document persistence layer
```

---

### Task 5: `DocumentService`

**Files:**
- Create: `DocumentService`
- Test: `DocumentServiceTest` (mocks repository + storage + validator + Clock)

**Interfaces:**
```java
List<DocumentItem> list(UUID propertyId);
DocumentItem upload(UUID propertyId, String documentTypeRaw, MultipartFile file);
SignedUrlResult signedUrl(UUID documentId);
void softDelete(UUID documentId);
```
- `list` : si !propertyExists → `PROPERTY_NOT_FOUND` ; sinon list (peut être vide).
- `upload` : orgId depuis bien ; `DocumentType` parse → `DOCUMENT_TYPE_INVALID` ; validate file ; new documentId ; build key ; **insert puis put** ; si put throws → wrap `DOCUMENT_STORAGE_UNAVAILABLE` (503) et laisser `@Transactional` rollback.
- `signedUrl` : findActive → `DOCUMENT_NOT_FOUND` ; `expiresAt = clock.instant().plus(ttl)` ; presign avec même ttl.
- `softDelete` : si !softDelete → `DOCUMENT_NOT_FOUND`.
- `@Transactional` sur upload et softDelete.

- [ ] **Step 1: Tests service** (list 404 ; upload happy path insert+put ; put fail → 503 et pas de commit simulé ; signedUrl ; double delete).

- [ ] **Step 2: Implémenter service.**

- [ ] **Step 3: `.\mvnw.cmd "-Dtest=DocumentServiceTest"` test** — PASS.

- [ ] **Step 4: Commit**

```text
feat: orchestrate document upload and soft-delete
```

---

### Task 6: Controller, CORS, WebMvcTest

**Files:**
- Create: `DocumentController`
- Modify: `CorsConfig` — methods `GET,POST,DELETE,OPTIONS` ; headers `Accept,Content-Type`
- Test: `DocumentControllerTest` (`@WebMvcTest` + mocks service + `DocumentProperties`)

**Interfaces:**
- `GET /api/v1/properties/{id}/documents` → 200 `ApiResponse<List<DocumentItem>>` (pas de drapeau)
- `POST /api/v1/properties/{id}/documents` multipart `file` + `documentType` → 201 (drapeau)
- `POST /api/v1/documents/{id}/signed-url` → 200 (drapeau)
- `DELETE /api/v1/documents/{id}` → 200 `ApiResponse` data null (drapeau)
- `requireMutationsEnabled()` : si false → `ApiException("NOT_FOUND", NOT_FOUND)` immédiat
- Ids : `UuidParam.parse(..., "PROPERTY_ID_INVALID"|"DOCUMENT_ID_INVALID")`

- [ ] **Step 1: Tests WebMvc** — drapeau off 404 sur 3 mutations même id invalide ; drapeau on 201/200 ; GET liste sans drapeau ; JSON sans `storageKey` ; 405 GET sur signed-url path ; codes 400/404/503 via service stub.

- [ ] **Step 2: Implémenter controller + CORS.**

- [ ] **Step 3: Commit**

```text
feat: expose document API endpoints and CORS mutations
```

---

### Task 7: Tests d’intégration PostGIS

**Files:**
- Create: `DocumentIT` (`@SpringBootTest` + Testcontainers PostGIS)
- `@TestConfiguration` : `@Primary InMemoryDocumentStorage` ; `mutations-enabled=true` ; Clock fixe optionnel
- Couvrir : Flyway V9 ; CHECK type/taille refusés via JDBC ; upload→list→signedUrl→delete→list vide ; second delete `DOCUMENT_NOT_FOUND` ; property soft-deleted / sans géométrie → 404 list+upload ; drapeau false → 404 mutations (test `@DynamicPropertySource` ou profile dédié)

**Interfaces:**
- Ne pas démarrer MinIO réel dans IT si stub `@Primary` présent.
- Seed biens existants OK ; aucun seed document.

- [ ] **Step 1: Écrire IT** selon spec § Backend, intégration.

- [ ] **Step 2: `.\mvnw.cmd verify`** — BUILD SUCCESS.

- [ ] **Step 3: Commit**

```text
test: cover document API against PostGIS
```

---

### Task 8: Frontend fiche Documents

**Files:**
- Modify: `frontend/lib/api/types.ts` — `DOCUMENT_TYPES`, `DocumentItem`, `SignedUrlResult`
- Create: `frontend/lib/api/documents.ts`
- Create: `DocumentList.tsx`, `DocumentList.test.tsx`, `DocumentUpload.tsx`, `DocumentUpload.test.tsx`
- Modify: `PropertyDetailView.tsx` — section sous sources ; query `['property-documents', id]`
- Modify: `messages/{pt,fr,en}/property.json` + `errors.json`
- Modify: `frontend/.env.example` — `NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED=true`

**Interfaces:**
- Mutations UI si `process.env.NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED === 'true'`
- Upload : `FormData` avec `file` + `documentType`
- Download : `requestDocumentSignedUrl` puis `window.open(url, '_blank', 'noopener,noreferrer')`
- Delete : `window.confirm(t('documents.deleteConfirm'))` puis DELETE + `invalidateQueries`
- i18n : clés spec ; libellés `documentType.*` en pt d’abord puis fr/en
- Parité des clés pt/fr/en (même structure JSON)

Libellés pt suggérés :
- `documents.title` = `Documentos`
- `documents.empty` = `Nenhum documento registado.`
- `documentType.titre_foncier` = `Título fundiário`
- `documentType.contrat_vente` = `Contrato de venda`
- (compléter les 10 de façon cohérente ; fr/en équivalents)

- [ ] **Step 1: Types + API client + i18n + composants + tests + brancher fiche.**

- [ ] **Step 2: `npm test`** (dans `frontend/`) — PASS.

- [ ] **Step 3: Commit**

```text
feat: show property documents section with upload
```

---

### Task 9: README + env examples + vérification finale

**Files:**
- Modify: `README.md` — curls PowerShell, drapeaux, `APP_STORAGE_*`, soft-delete ≠ purge MinIO
- Modify: `.env.example` — commenter `APP_STORAGE_ENDPOINT`, `APP_STORAGE_ACCESS_KEY`, `APP_STORAGE_SECRET_KEY`, `APP_STORAGE_REGION`

- [ ] **Step 1: README + `.env.example`.**

- [ ] **Step 2: `.\mvnw.cmd verify` + `npm test`** — PASS.

- [ ] **Step 3: Commit**

```text
docs: document Bloc 4 MinIO upload and signed URLs
```

- [ ] **Step 4: Finishing (merge / PR / keep) si demandé.

---

## Plan self-review

1. **Spec coverage:** V9, validation Tika/whitelist/50Mo, drapeau 404, GET liste ouverte, signed-url 15m, soft-delete sans remove, CORS, DocumentItem sans storageKey, UI fiche + flag public, errors i18n, README, IT stub — Tasks 1–9. Pas de seed binaires. Pas de confiance/JWT/audit.
2. **Placeholders:** aucun TBD ; deps verrouillées AWS SDK v2 + Tika ; IT = stub storage explicite.
3. **Types:** `DocumentItem` / `SignedUrlResult` / `DocumentStorage` cohérents Tasks 3–8.
4. **Ordre upload:** insert puis put + rollback — Task 5.
5. **CORS:** Task 6 (requis pour UI Task 8).
