# Bloc 4 — Gestion documentaire — Design

Date : 2026-11-13  
Statut : en relecture  
Périmètre : preuve verticale mince (approche A) — documents liés à un bien, upload via Spring → MinIO, URL signée 15 min, soft-delete, section sur la fiche, sans JWT ni audit UI  
Dépend de : Cycle 0 (révision 2), Blocs 1–3

Ce bloc **ajoute** la table `document`, les endpoints documents, l’adapter MinIO, et une section Documents sur la fiche bien. Il **étend** le CORS du cycle 0 (aujourd’hui `GET`/`OPTIONS` seulement) pour autoriser les mutations navigateur → API. Il **n’ajoute pas** de seed de documents binaires.

## Décisions

- Approche **A** : le navigateur envoie le fichier en multipart à Spring ; Spring valide (extension, taille, MIME Tika, type métier), persiste les métadonnées, puis `PutObject` dans le bucket `documents` déjà créé par `minio-init`.
- Migration **`V9`** (`V8` = moteur de confiance). Table **`document`** avec `property_id` et `organisation_id`. Pas de `media`, pas de `document_entity_link` (reportés quand parcelle / dossier existent).
- Soft-delete : colonne `deleted_at` ; **jamais** de suppression d’objet MinIO. La liste n’affiche que `deleted_at IS NULL`.
- Drapeau **`app.documents.mutations-enabled`** : `false` par défaut (`application.yml`), `true` dans `application-dev.yml`. Tant qu’il est faux, `POST` upload, `POST` signed-url et `DELETE` répondent **404 `NOT_FOUND` avant** toute validation d’identifiant (même convention que le recalcul de confiance).
- `GET /api/v1/properties/{id}/documents` **toujours** autorisé (même niveau que la fiche et les sources).
- Type métier **fermé** (10 valeurs du PRD, stockées telles quelles) : `titre_foncier`, `attestation_possession`, `contrat_vente`, `contrat_location`, `plan_parcelle`, `photo_terrain`, `piece_identite_masquee`, `acte_notarie`, `document_administratif`, `autre`.
- Pas de statut de vérification documentaire, pas d’historique d’accès, pas de journal d’audit (Bloc 7), pas de JWT, pas de recalcul de confiance à l’upload, pas d’OCR, pas de virus scan, pas de SSE-S3 obligatoire, pas de Playwright requis.
- Accès données : **JdbcClient**. Pas de JPA / MapStruct / Bean Validation / Spring Security.
- Stockage : interface `DocumentStorage` + implémentation S3-compatible (AWS SDK v2 `s3` pointant vers MinIO). Credentials et endpoint via variables d’environnement / config ; jamais de secret dans le dépôt.
- UI : section Documents sous les sources sur la fiche. Contrôles d’écriture visibles si `NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED=true` (miroir UX) ; l’API reste la source de vérité.
- Seed : **aucun** document. Démo via upload manuel en profil `dev`.
- Orphelins MinIO (put réussi, commit SQL échoué ensuite) : **tolérés** dans ce bloc ; pas de job de nettoyage.

## Critère de succès

1. Flyway applique `V9` ; table `document` avec CHECK type, taille et unicité de `storage_key`.
2. Avec `app.documents.mutations-enabled=true` (profil `dev`) : upload d’un PDF valide sur un bien seed → 201 + métadonnées ; `GET …/documents` le liste ; `POST …/signed-url` renvoie une URL exploitable ≤ 15 min ; `DELETE` → le document disparaît de la liste ; l’objet reste dans MinIO.
3. Avec le drapeau à `false` : les trois mutations répondent 404 `NOT_FOUND` (même avec un id invalide) ; le `GET` liste reste 200.
4. Rejet : extension hors whitelist, MIME Tika non conforme, fichier > 50 Mo, `documentType` inconnu, bien absent / soft-deleted / sans géométrie.
5. La fiche affiche la section Documents ; upload / télécharger / supprimer selon le flag frontend ; clés i18n `pt` / `fr` / `en`.
6. `./mvnw verify` et `npm test` passent ; les tests des Blocs 0 à 3 restent verts.

## Hors de ce bloc

Spring Security, JWT, `@PreAuthorize`, statut de vérification document, historique d’accès, audit UI / AOP (Bloc 7), `document_entity_link`, table `media`, PUT direct navigateur→MinIO, virus scan, SSE-S3, OCR, recalcul confiance post-upload, Playwright, page `/documents` dédiée, seed de binaires.

## Architecture

```text
GET    /api/v1/properties/{id}/documents     (toujours)
POST   /api/v1/properties/{id}/documents     } drapeau mutations
POST   /api/v1/documents/{id}/signed-url     }
DELETE /api/v1/documents/{id}                }
        ↓
DocumentController
        ↓
DocumentService
  list        → DocumentRepository
  upload      → validate → INSERT → DocumentStorage.put → (rollback SQL si put échoue)
  signed-url  → load active → DocumentStorage.presignGet (15 min)
  soft-delete → UPDATE deleted_at (pas de remove MinIO)
        ↓
DocumentRepository (JdbcClient)
DocumentStorage (MinIO / S3)
```

Packages sous `com.immodata.gb` :

```text
domain.document.DocumentType
application.document.DocumentService
application.document.DocumentItem
application.document.SignedUrlResult
infrastructure.persistence.document.DocumentRepository
infrastructure.storage.DocumentStorage
infrastructure.storage.MinioDocumentStorage   (ou S3DocumentStorage)
infrastructure.config.DocumentProperties
api.document.DocumentController
```

Réutiliser `UuidParam` et `ApiException` / `ApiExceptionHandler` existants.

### CORS (changement cycle 0)

`CorsConfig` autorise aujourd’hui seulement `GET` et `OPTIONS` avec header `Accept`. Pour que la fiche puisse uploader / supprimer / demander une URL signée depuis `http://localhost:3000` :

- méthodes : `GET`, `POST`, `DELETE`, `OPTIONS` ;
- headers : au minimum `Accept`, `Content-Type` (multipart).

Pas de credentials CORS. Origines toujours via `app.cors.allowed-origins`.

## Modèle de données

### `V9__create_document.sql`

Table `document` :

| Colonne | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `organisation_id` | UUID NOT NULL | FK `organisation(id)` ; copié depuis le bien à l’insert |
| `property_id` | UUID NOT NULL | FK `property(id)` |
| `document_type` | VARCHAR(40) NOT NULL | CHECK : les 10 valeurs PRD |
| `original_filename` | VARCHAR(500) NOT NULL | nom client ; `btrim` non vide |
| `content_type` | VARCHAR(200) NOT NULL | MIME validé |
| `size_bytes` | BIGINT NOT NULL | CHECK `> 0` et `<= 52428800` (50 Mo) |
| `storage_bucket` | VARCHAR(100) NOT NULL | valeur attendue `documents` |
| `storage_key` | VARCHAR(1000) NOT NULL | UNIQUE |
| `created_at` / `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `now()` | |
| `created_by` / `updated_by` | UUID NULL | aligné `property` ; non renseigné sans JWT |
| `deleted_at` | TIMESTAMPTZ NULL | soft-delete |

Contraintes / index :

- `document_type_check` sur les 10 littéraux ;
- `document_size_bytes_check` ;
- `document_original_filename_check` : `length(btrim(original_filename)) > 0` ;
- UNIQUE `storage_key` ;
- index `(property_id, created_at DESC, id)` WHERE `deleted_at IS NULL` ;
- index `(organisation_id)` WHERE `deleted_at IS NULL`.

### Clé de stockage

Format fixe :

```text
{organisation_id}/{property_id}/{document_id}/{safe_filename}
```

`safe_filename` : stem alphanumérique (caractères hors `[A-Za-z0-9._-]` remplacés par `_`), longueur bornée, **extension** reprise exclusivement depuis la whitelist validée (pas depuis un MIME spoofé seul). Exemple : `contrat final.PDF` → stem `contrat_final` + `.pdf`.

### Seed

Aucun `INSERT` dans `document`. Le fichier `R__seed_bissau_dev.sql` n’est **pas** modifié pour des documents.

## Validation upload

Ordre :

1. Drapeau mutations (sinon 404 `NOT_FOUND`).
2. Bien actif avec géométrie (sinon `PROPERTY_NOT_FOUND`) ; lecture de `organisation_id`.
3. Champ `file` présent et non vide (sinon `DOCUMENT_FILE_MISSING`).
4. Champ `documentType` ∈ enum (sinon `DOCUMENT_TYPE_INVALID`).
5. Taille `1 … 52428800` (sinon `DOCUMENT_FILE_TOO_LARGE` ; 0 octet = `DOCUMENT_FILE_MISSING` ou trop large selon détection — **0 octet → `DOCUMENT_FILE_MISSING`**).
6. Extension du nom d’origine ∈ `{pdf,jpg,jpeg,png,webp,heic,docx,xlsx}` (casse ignorée) ; sinon `DOCUMENT_FILE_TYPE_REJECTED`.
7. Détection MIME Apache Tika ; doit correspondre à l’extension selon la table ci-dessous ; sinon `DOCUMENT_FILE_TYPE_REJECTED`.

| Extension | MIME acceptés (Tika) |
|---|---|
| `pdf` | `application/pdf` |
| `jpg`, `jpeg` | `image/jpeg` |
| `png` | `image/png` |
| `webp` | `image/webp` |
| `heic` | `image/heic`, `image/heif` |
| `docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| `xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

Limite Spring multipart : configurer `spring.servlet.multipart.max-file-size` et `max-request-size` à **50MB** (profil commun), pour que le rejet applicatif et le rejet conteneur restent alignés.

Échec MinIO après INSERT → exception → rollback transaction → `DOCUMENT_STORAGE_UNAVAILABLE` (503).

## API

### `DocumentItem`

```json
{
  "id": "…",
  "propertyId": "…",
  "documentType": "contrat_vente",
  "originalFilename": "contrat.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 12345,
  "createdAt": "2026-11-13T16:00:00Z"
}
```

Jamais : `storageKey`, `storageBucket`, `organisationId`, URL permanente, chemins disque.

### `SignedUrlResult`

```json
{
  "url": "https://…",
  "expiresAt": "2026-11-13T16:15:00Z"
}
```

TTL **exactement 15 minutes** à partir de l’horloge serveur (`Clock` UTC déjà présent). `expiresAt` = instant de génération + 15 min.

### Endpoints

**`GET /api/v1/properties/{id}/documents`**  
→ `ApiResponse<List<DocumentItem>>`, ordre `created_at DESC, id`.  
Erreurs : `PROPERTY_ID_INVALID` (400), `PROPERTY_NOT_FOUND` (404 : absent, soft-deleted ou sans géométrie). Bien sans documents → 200 + `[]`. Pas de pagination dans ce bloc.

**`POST /api/v1/properties/{id}/documents`** (multipart)  
Champs : `file` (obligatoire), `documentType` (obligatoire).  
→ 201 `ApiResponse<DocumentItem>`.  
Drapeau off → 404 `NOT_FOUND`. Autres : validations ci-dessus ; `DOCUMENT_STORAGE_UNAVAILABLE` (503).

**`POST /api/v1/documents/{id}/signed-url`**  
Sans corps. → 200 `ApiResponse<SignedUrlResult>`.  
Drapeau off → 404 `NOT_FOUND`. Document inconnu ou soft-deleted → `DOCUMENT_NOT_FOUND`. Id invalide → `DOCUMENT_ID_INVALID`.

**`DELETE /api/v1/documents/{id}`**  
Soft-delete. → 200 `ApiResponse` avec `data: null` (succès homogène). Idempotent métier : un second DELETE sur le même id → `DOCUMENT_NOT_FOUND` (déjà soft-deleted = introuvable).  
Drapeau off → 404 `NOT_FOUND`. Pas de remove MinIO.

`GET` sur les chemins mutation-only → 405 `METHOD_NOT_ALLOWED` existant.

Codes d’erreur nouveaux (stables, traduits frontend) :

| Code | HTTP |
|---|---|
| `DOCUMENT_ID_INVALID` | 400 |
| `DOCUMENT_NOT_FOUND` | 404 |
| `DOCUMENT_TYPE_INVALID` | 400 |
| `DOCUMENT_FILE_MISSING` | 400 |
| `DOCUMENT_FILE_TOO_LARGE` | 400 |
| `DOCUMENT_FILE_TYPE_REJECTED` | 400 |
| `DOCUMENT_STORAGE_UNAVAILABLE` | 503 |

Plus `NOT_FOUND` (drapeau), `PROPERTY_*`, `HEALTH_DATABASE_UNAVAILABLE`, `INTERNAL_ERROR`.

### Configuration

```yaml
# application.yml
app:
  documents:
    mutations-enabled: false
    bucket: documents
    signed-url-ttl: 15m

spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

# application-dev.yml
app:
  documents:
    mutations-enabled: true
  storage:
    endpoint: ${APP_STORAGE_ENDPOINT:http://127.0.0.1:9000}
    access-key: ${APP_STORAGE_ACCESS_KEY:minioadmin}
    secret-key: ${APP_STORAGE_SECRET_KEY:minioadmin}
    region: ${APP_STORAGE_REGION:us-east-1}
```

`.env.example` : documenter `APP_STORAGE_*` (valeurs jetables localhost). Pas de secret nouveau hardcodé hors profil `dev` / example.

## Frontend

- `lib/api/types.ts` : `DocumentItem`, `DocumentType` (union des 10 littéraux), `SignedUrlResult`.
- `lib/api/documents.ts` : `fetchPropertyDocuments`, `uploadPropertyDocument`, `requestDocumentSignedUrl`, `deleteDocument`.
- `PropertyDetailView` : après sources, section Documents.
- `DocumentList` : type traduit, nom, taille formatée, date ; boutons Télécharger / Supprimer si mutations UI on.
- `DocumentUpload` : `<select>` des types + `<input type="file">` + soumission ; visible seulement si `NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED === 'true'`.
- Télécharger : POST signed-url puis ouverture de `url` (nouvel onglet / navigation) ; **ne jamais** persister l’URL.
- Supprimer : confirmation native `window.confirm` (texte i18n) puis DELETE + invalidation query `['property-documents', id]`.
- Erreurs API : clés dans `messages/{pt,fr,en}/errors.json`.

Clés i18n (rédigées d’abord en portugais) :

| Clé | Rôle |
|---|---|
| `property.documents.title` | Titre de section |
| `property.documents.empty` | Liste vide |
| `property.documents.upload` | Libellé bouton / formulaire |
| `property.documents.download` | Action |
| `property.documents.delete` | Action |
| `property.documents.deleteConfirm` | Confirmation |
| `property.documents.size` | Affichage taille |
| `property.documents.type` | Label select |
| `property.documentType.*` | 10 libellés |
| `errors.DOCUMENT_*` | Messages d’erreur |

`frontend/.env.example` : `NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED=true` pour le flux local documenté.

## Tests

### Backend, unitaires

- `DocumentType` : parsing des 10 valeurs ; rejet des inconnues.
- Validation fichier : extension / MIME table ; rejet spoof (ex. `.pdf` avec contenu texte) ; taille 0 / > 50 Mo.
- Construction `storage_key` / `safe_filename`.

### Backend, `@WebMvcTest` `DocumentController`

- drapeau off : 404 `NOT_FOUND` sur POST upload, POST signed-url, DELETE, même avec id invalide ;
- drapeau on : 201 upload (storage mocké), 200 liste, 200 signed-url, 200 delete ;
- 400 / 404 / 503 sur les codes document ;
- 405 sur GET des routes mutation-only ;
- forme JSON : pas de `storageKey` / `storageBucket`.

### Backend, intégration

Testcontainers PostGIS ; `DocumentStorage` :

- **recommandé** : Testcontainers MinIO si stable dans la CI locale ; sinon stub `DocumentStorage` en mémoire / filesystem temporaire **documenté**, plus un test unitaire de l’adapter MinIO avec client mocké.
- Flyway `V9` ; contraintes CHECK (type, taille) ;
- upload → list → soft-delete → list vide ; signed-url TTL / présence d’URL ;
- bien soft-deleted / sans géométrie → 404 sur list et upload ;
- second DELETE → `DOCUMENT_NOT_FOUND` ;
- drapeau false dans un test de config dédié → 404 mutations.

### Frontend

Vitest :

- `DocumentList` vide / avec item (type traduit, nom) ;
- masquage de `DocumentUpload` si flag off ;
- parité des clés i18n `pt` / `fr` / `en`.

## Contrôle manuel (README)

Avec profil `dev` (drapeaux documents + MinIO up) :

```text
curl.exe -F "file=@.\contrat.pdf;type=application/pdf" -F "documentType=contrat_vente" ^
  "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000001/documents"

curl.exe "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000001/documents"

curl.exe -X POST "http://localhost:8080/api/v1/documents/{id}/signed-url"

curl.exe -X DELETE "http://localhost:8080/api/v1/documents/{id}"
```

Documenter : `app.documents.mutations-enabled`, `NEXT_PUBLIC_DOCUMENTS_MUTATIONS_ENABLED`, `APP_STORAGE_*`, bucket `documents`, et le fait que soft-delete ne purge pas MinIO.

## Critères d’acceptation

- [ ] Upload multipart validé (whitelist, 50 Mo, Tika) vers MinIO + métadonnées PostgreSQL
- [ ] Liste lecture seule sur la fiche ; mutations derrière drapeau (404 si off)
- [ ] URL signée GET 15 min ; aucune URL permanente exposée
- [ ] Soft-delete sans suppression physique MinIO ni SQL hard delete
- [ ] Type métier fermé PRD ; i18n pt/fr/en
- [ ] CORS étendu pour POST/DELETE depuis le frontend local
- [ ] Pas de JWT, audit UI, OCR, statut de vérif, ni recalcul confiance dans ce bloc
- [ ] `./mvnw verify` et `npm test` verts

## Points à vérifier à la génération

- Choix exact de l’artefact Maven (AWS SDK v2 `software.amazon.awssdk:s3` vs `io.minio:minio`) et config path-style pour MinIO local.
- Comportement Tika sur `heic` / `docx` / `xlsx` dans l’environnement de test ; ajuster la table MIME si Tika renvoie un sous-type équivalent documenté.
- Testcontainers MinIO : image alignée sur `pgsty/minio` ou image officielle testcontainers ; sinon stub storage pour IT.
- Limites multipart Spring vs Tomcat : s’assurer que le code d’erreur applicatif est atteint avant un 413 opaque, ou mapper 413 → `DOCUMENT_FILE_TOO_LARGE`.
- Ouverture d’URL MinIO signée depuis le navigateur (nouvel onglet) sans config CORS MinIO supplémentaire.

## Notes pour blocs suivants

- Identité / RBAC : remplacer le drapeau par `@PreAuthorize` ; renseigner `created_by` / `updated_by`.
- Bloc 7 : journaliser upload, lecture (signed-url), soft-delete.
- Après CRUD sources/documents : déclencher le recalcul de confiance du bien (note Bloc 3).
- `document_entity_link` polymorphe pour parcelle / dossier ; table `media` si dérivés (miniatures).
- Statut de vérification document + impact éventuel sur `doc_status` du bien.
- Nettoyage périodique des orphelins MinIO et, plus tard, purge physique optionnelle des soft-deleted (hors règle « jamais de suppression physique » des biens/sources/documents métier — toute purge devra être une décision produit explicite).
