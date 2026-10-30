# Bloc 2 — Fiches biens (lecture) — Design

Date : 2026-10-30  
Statut : en relecture  
Périmètre : preuve verticale mince (approche A) — fiche lecture seule + sources, sans authentification  
Dépend de : Bloc 1 — Cartographie (révision 2)

## Décisions

- Approche **A** : page fiche dédiée + table `source` + colonnes `doc_status` / `address_text`.
- Navigation : lien dans la **popup carte** (« Voir la fiche ») vers `/[locale]/properties/[id]`. Pas de drawer, pas de navigation au simple clic marqueur.
- Accès données : **JdbcClient** (comme le Bloc 1). Pas de JPA / MapStruct / Bean Validation / Spring Security.
- Confiance reste figée en colonnes. Pas de moteur configurable (Bloc 3).
- Pas de CRUD, verify/reject, documents MinIO, historique, prix, surfaces, JWT.
- `GET /api/v1/properties/{id}` renvoie désormais un **`PropertyDetail`** (surensemble de `PropertyMapItem`). La liste spatiale (`GET /properties`) reste en `PropertyMapItem` inchangé (sans `docStatus` / `addressText` dans ce bloc, pour limiter le diff carte).
- `GET /api/v1/properties/{id}/sources` : liste ; 404 si le bien est absent ou soft-deleted ; 200 + `[]` si le bien existe sans sources.
- Soft delete : `property.deleted_at` ; les sources n’ont pas de `deleted_at` (elles disparaissent avec le bien côté API).
- Seed : profil `dev` uniquement (`db/seed`), migration répétable mise à jour de façon idempotente.

## Critère de succès

1. Flyway applique `V6`–`V7` ; le seed enrichit les 24 biens et crée des sources.
2. `GET /api/v1/properties/{id}` (UUID seed) renvoie détail avec `docStatus`, `confidenceSummary`, et éventuellement `addressText`.
3. `GET /api/v1/properties/{id}/sources` renvoie au moins une source pour un bien seed choisi.
4. Depuis `/pt/map`, popup → lien → fiche avec badges confiance + preuve + liste sources.
5. `./mvnw verify` et `npm test` passent, tests Bloc 0 et Bloc 1 verts.

## Hors de ce bloc

Spring Security, JWT, JPA, MapStruct, Bean Validation, CRUD, verify/reject, documents, historique, estimation, moteur de confiance, Playwright obligatoire, parcelle/dossier comme types d’entité séparés, filtre org.

## Architecture

```text
Carte /map — PropertyPopup
  → Link /[locale]/properties/{id}
  → PropertyDetailView
       → GET /api/v1/properties/{id}        → PropertyDetail
       → GET /api/v1/properties/{id}/sources → List<SourceItem>
```

## Modèle de données

### `V6__alter_property_detail_fields.sql`

Sur `property` :

| Colonne | Notes |
|---|---|
| `doc_status` VARCHAR(30) NOT NULL DEFAULT `'none'` | CHECK : `none` \| `declared` \| `partial` \| `verified` \| `litigious` |
| `address_text` TEXT NULL | |

Pas d’autre colonne (prix, surface, etc.).

### `V7__create_source.sql`

Table `source` :

| Colonne | Notes |
|---|---|
| `id` UUID PK | `gen_random_uuid()` |
| `property_id` UUID NOT NULL | FK `property(id)` |
| `source_type` VARCHAR(30) NOT NULL | CHECK : `agency_listing` \| `field_observation` \| `scanned_doc` \| `institutional` \| `third_party_user` \| `partner` \| `open_layer` |
| `observed_at` DATE NULL | |
| `is_verified` BOOLEAN NOT NULL DEFAULT false | |
| `weight` DECIMAL(3,2) NOT NULL DEFAULT 1.0 | |
| `created_at` TIMESTAMPTZ NOT NULL DEFAULT now() | |

Index `(property_id)`. Pas de `contributor_id` / `raw_data` (reportés).

### Seed (`R__seed_bissau_dev.sql` ou fichier companion répétable)

Étendre le seed existant de façon **idempotente** :

- `UPDATE property … SET doc_status, address_text` selon règles déterministes sur `n` (référence `BIS-00n`), ou `INSERT` colonnes si le seed properties est régénéré en entier.
- Préférence : **régénérer** le seed properties avec les nouvelles colonnes (fichier unique maintenu), plus inserts `source` `ON CONFLICT (id) DO NOTHING`.
- UUID sources : `00000000-0000-0000-0003-0000000000NNMM` (NN = bien, MM = rang source) — documentés en tête de fichier.
- Règles suggérées (à figer dans le seed) :
  - `doc_status` : cycle `[none, declared, partial, verified, litigious]` sur `(n-1) % 5`.
  - `address_text` : non null si `n % 3 != 0`, texte portugais court « Bairro …, Bissau ».
  - Nombre de sources : `1 + (n % 3)` (donc 1, 2 ou 3).
  - Si `confidence_level = high` : au moins une source avec `is_verified = true`.
  - Types sources : rotation sur `field_observation`, `agency_listing`, `institutional`.

## API

### `PropertyDetail`

Record (JSON camelCase) :

`id`, `organisationId`, `reference`, `title`, `propertyType`, `confidenceScore`, `confidenceLevel`, `confidenceSummary`, `longitude`, `latitude`, `docStatus`, `addressText` (null si absent).

### `SourceItem`

`id`, `propertyId`, `sourceType`, `observedAt` (string ISO date ou null), `isVerified`, `weight`.

### Endpoints

**`GET /api/v1/properties/{id}`**  
Réponse `ApiResponse<PropertyDetail>`. Remplace l’ancien corps `PropertyMapItem` sur cette route.  
Erreurs : `PROPERTY_ID_INVALID`, `PROPERTY_NOT_FOUND`.

**`GET /api/v1/properties/{id}/sources`**  
Réponse `ApiResponse<List<SourceItem>>`, ordre `created_at ASC, id`.  
Même validation id ; 404 si bien absent/deleted ; 200 + `[]` sinon.

**`GET /api/v1/properties` (liste)**  
Inchangé (`PropertyMapItem` sans les nouveaux champs).

Enveloppe, `Cache-Control: no-store`, handlers d’erreur : inchangés.

## Packages Java

Étendre / ajouter :

```text
com.immodata.gb.domain.property.DocStatus
com.immodata.gb.domain.property.SourceType
com.immodata.gb.application.property.PropertyDetail
com.immodata.gb.application.property.SourceItem
com.immodata.gb.infrastructure.persistence.property.PropertySpatialRepository  // + findDetailById, listSourcesByPropertyId
com.immodata.gb.application.property.PropertyQueryService                      // + getDetail, listSources
com.immodata.gb.api.property.PropertyController                                // ajuster get + nouveau mapping sources
```

`PropertyQueryService.get(UUID)` utilisé par la carte devient `getDetail` pour l’endpoint `{id}`, ou `get` est mis à jour pour renvoyer `PropertyDetail`. Les WebMvcTest / IT du Bloc 1 qui assertent la forme de `{id}` sont **mis à jour** pour `PropertyDetail` (champs supplémentaires).

## Frontend

### Fichiers

- `app/[locale]/properties/[id]/page.tsx` — serveur + `Suspense`
- `components/property/PropertyDetailView.tsx` — client, queries
- `components/property/DocStatusBadge.tsx`
- `components/property/SourceList.tsx`
- `lib/api/properties.ts` — `fetchPropertyDetail`, `fetchPropertySources`
- `lib/api/types.ts` — `PropertyDetail`, `SourceItem`, `DocStatus`
- `messages/{pt,fr,en}/property.json` — fusion dans `i18n/request.ts`
- Modifier `components/map/PropertyPopup.tsx` — `Link` vers `/properties/{id}`
- Réutiliser `components/map/ConfidenceBadge.tsx`

### UX fiche

- Titre, référence, type (libellés i18n `map.type.*` ou clés `property.*`).
- `addressText` seulement si non null / non vide.
- `ConfidenceBadge` + texte `confidenceSummary`.
- `DocStatusBadge` : couleurs distinctes par statut (pas la même palette que confiance) ; libellé traduit ; la couleur n’est jamais le seul indicateur.
- `SourceList` : type traduit, date si présente, pastille vérifié / non vérifié.
- Lien retour : `/map` (sans imposer `return=` dans ce bloc).
- États : skeleton ; 404 `property.notFound` ; autres erreurs via codes connus / `common.error`.

### Popup

Lien texte `map.popup.viewDetails` (pt : « Ver ficha », fr : « Voir la fiche », en : « View details ») sous le summary.

### Clés i18n (rédiger d’abord en portugais)

Inclure au minimum : `property.title`, `property.notFound`, `property.backToMap`, `property.address`, `property.sources.title`, `property.sources.empty`, `property.sources.verified`, `property.sources.unverified`, `property.docStatus.*` (5 valeurs), `property.sourceType.*` (types utilisés au seed), `map.popup.viewDetails`, erreurs déjà existantes pour 404 si besoin d’une clé UI `property.notFound` plutôt que `PROPERTY_NOT_FOUND`.

## Tests

### Backend

- WebMvcTest : détail 200 forme `PropertyDetail` ; 400 id ; 404 ; sources 200 ; sources 404 si service lève NOT_FOUND.
- IT : seed détail + sources ; soft-delete → 404 sur les deux routes ; liste spatiale toujours 24 biens.

### Frontend

- `DocStatusBadge`, `SourceList`, clients API.
- Parité clés `property.json`.
- Contrôle manuel README (popup → fiche → retour).

## Critères d’acceptation

- [ ] Lien popup → fiche localisée
- [ ] Badges confiance et `doc_status` distincts + summary
- [ ] Sources seed listées
- [ ] Soft-delete → 404
- [ ] Liste carte inchangée fonctionnellement
- [ ] Pas de JWT / CRUD / upload / historique
- [ ] Tests Blocs 0–1 toujours verts

## Points à vérifier à la génération

- Migration `V6`/`V7` sans toucher `V1`–`V5` / seed déjà appliqué : sur base existante, `docker compose down -v` si le seed répétable change de forme.
- Compat Jackson : `observedAt` en `LocalDate` → ISO-8601.
- `Link` next-intl depuis popup client (carte déjà client).

## Notes pour blocs suivants

- Bloc identité : protéger détail / sources selon rôle ; historiser lectures sensibles.
- Bloc 3 : recalcul `confidence_*` à partir des sources.
- Bloc 4 : documents liés ; ne pas confondre avec `source_type = scanned_doc` sans fichier.
- CRUD : JPA envisageable alors ; endpoints write + `doc_status` workflow.
