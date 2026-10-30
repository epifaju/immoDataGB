# Bloc 1 — Cartographie interactive — Design

Date : 2026-10-30  
Statut : en relecture  
Périmètre : preuve verticale mince (approche A) — carte + liste + bbox PostGIS, sans authentification

## Décisions

- Approche **A** : markers GeoJSON/lon-lat via REST paginé + liste synchronisée. Pas de tuiles Martin pour les biens, pas de clusters, pas de polygones, pas de limites administratives.
- Pas de JWT : les endpoints carte sont publics (`permitAll`) jusqu’au bloc identité. Spring Security est introduit uniquement pour porter `@PreAuthorize` et une chaîne « tout ouvert » en local.
- Confiance **figée en colonnes** sur `property` (`confidence_score`, `confidence_level`, `confidence_summary`). Le moteur configurable (Bloc 3) est hors scope.
- Fiche complète / CRUD / sources / documents = Bloc 2. Le clic marqueur ouvre une **popup** (résumé), pas une navigation fiche.
- Stack données : **JPA + MapStruct** + requête native PostGIS (`ST_Within` + index GIST).
- Seed **Flyway** : une organisation pilote + ~20 biens ponctuels dans l’emprise Bissau, types et niveaux de confiance variés.
- Filtres URL de ce cycle : `bbox`, `type`, `confidence`, `page` (et `size` côté API seulement). Le filtre `status` / `commercial_status` du PRD est reporté (colonne absente du schéma mince).
- Soft delete : `deleted_at TIMESTAMPTZ NULL` (pas de suppression physique). Les requêtes excluent `deleted_at IS NOT NULL`.
- Géométrie dans table dédiée `property_geometry`, SRID 4326, type `POINT` pour ce cycle (`geom_type = 'point'`). Jamais de lat/lng seuls en base.
- Emprise Bissau par défaut (constante front + seed) : `bbox=-15.70,-11.95,-15.50,-11.80` (w,s,e,n).
- Dépendances nouvelles backend : `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, MapStruct (+ processor), Hibernate Spatial ou extraction `ST_X`/`ST_Y` en SQL natif (préférer **SQL natif** pour rester mince).
- Dépendances nouvelles frontend : `maplibre-gl`, `react-map-gl` (branche MapLibre compatible Next 14), éventuellement `zustand` seulement si l’état sélection carte/liste le justifie ; sinon état local React + URL.

## Critère de succès

1. Compose + Flyway V2–V5 : org + properties + géométries + seed chargés.
2. `GET /api/v1/properties?bbox=-15.70,-11.95,-15.50,-11.80` renvoie une page non vide de biens seed, enveloppe `ApiResponse` + `PageResponse`.
3. `http://localhost:3000/pt/map` (ou port de secours documenté) affiche fond OSM, marqueurs, liste sync, popup au clic, badge confiance + summary.
4. Changer filtres / pan-zoom met à jour l’URL ; coller l’URL restaure l’état.
5. `./mvnw verify` et `npm test` passent (tests listés plus bas).

## Hors de ce cycle

JWT / rôles métier, clusters, Martin MVT biens, polygones, `admin_boundary`, couches SIG, fiche détail navigable, CRUD, sources, documents, estimation, moteur de confiance, Playwright carte obligatoire, import CSV, saisie terrain, back-office.

Le catalogue Martin peut rester vide.

## Architecture

```text
Navigateur /[locale]/map?bbox&type&confidence&page
  → Next.js (CSR carte)
  → GET {NEXT_PUBLIC_API_URL}/api/v1/properties?...
  → PropertyController
  → PropertyQueryService + PropertySpatialRepository (PostGIS)
  → PageResponse<PropertyMapItemDto>
  → marqueurs MapLibre + liste

Clic marqueur | clic liste
  → sélection locale + popup depuis l’item déjà chargé (pas de fetch supplémentaire)
```

Le détail mince `GET /properties/{id}` existe pour le contrat API et les tests 404. L’UI carte/liste **n’appelle pas** cet endpoint dans ce cycle.

## Modèle de données (mince)

### `V2__create_organisation.sql`

Table `organisation` : `id` UUID PK, `name`, `created_at`, `updated_at`, `deleted_at`. Suffisant pour `organisation_id` tenant.

### `V3__create_property.sql`

Table `property` (sous-ensemble PRD) :

| Colonne | Notes |
|---|---|
| `id` UUID PK | `gen_random_uuid()` |
| `organisation_id` UUID NOT NULL | FK organisation |
| `reference` VARCHAR(50) UNIQUE NOT NULL | |
| `title` VARCHAR(500) NOT NULL | |
| `property_type` VARCHAR(50) NOT NULL | `house` \| `apartment` \| `land` \| `commercial` |
| `confidence_score` SMALLINT NOT NULL | 0–100 |
| `confidence_level` VARCHAR(10) NOT NULL | `low` \| `medium` \| `high` |
| `confidence_summary` TEXT NOT NULL | obligatoire |
| `created_at` / `updated_at` | TIMESTAMPTZ |
| `created_by` / `updated_by` | UUID NULL (pas de FK users) |
| `deleted_at` | TIMESTAMPTZ NULL |

Contraintes CHECK sur score et level. Index `(organisation_id)`, `(property_type)`, `(confidence_level)`, `(deleted_at)`.

Pas de `admin_boundary_id`, prix, sources, ni `is_deleted` booléen séparé : le soft delete est `deleted_at`.

### `V4__create_property_geometry.sql`

| Colonne | Notes |
|---|---|
| `id` UUID PK | |
| `property_id` UUID UNIQUE NOT NULL | FK property, 1 géométrie affichable pour ce cycle |
| `geom` geometry(Point, 4326) NOT NULL | |
| `geom_type` VARCHAR(20) NOT NULL | `'point'` |
| `precision_level` VARCHAR(20) | ex. `gps` / `address` / `zone` |
| `created_at` | |

Index GIST sur `geom`.

### `V5__seed_bissau_properties.sql`

- 1 organisation « ImmoData Bissau Pilote » (UUID fixe documenté).
- ~20 properties + points à l’intérieur de l’enveloppe défaut, répartition `property_type` et `confidence_level`, summaries non vides en portugais (ou neutre court).

## API

### Contrats partagés

- `ApiResponse<T>` existant.
- Nouveau `PageResponse<T>` : `page` (0-based), `size`, `totalElements`, `totalPages`, `items` (liste).
- Défauts : `page=0`, `size=20`, `size` max `100`.

### `GET /api/v1/properties`

Query :

| Param | Règle |
|---|---|
| `bbox` | **obligatoire** `w,s,e,n` (4 doubles, `w < e`, `s < n`) |
| `type` | optionnel ; un de `house\|apartment\|land\|commercial` |
| `confidence` | optionnel ; un de `low\|medium\|high` (filtre sur **level**, pas le score) |
| `page`, `size` | pagination |

Réponse `data` : `PageResponse<PropertyMapItemDto>`.

`PropertyMapItemDto` : `id`, `organisationId`, `reference`, `title`, `propertyType`, `confidenceScore`, `confidenceLevel`, `confidenceSummary`, `longitude`, `latitude`.

SQL (intention) : join `property` ↔ `property_geometry`, `deleted_at IS NULL`, `ST_Within(geom, ST_MakeEnvelope(w,s,e,n,4326))`, filtres optionnels, `ORDER BY created_at DESC`, `ST_X`/`ST_Y` pour le DTO.

### `GET /api/v1/properties/{id}`

Même DTO. 404 `PROPERTY_NOT_FOUND` si absent ou soft-deleted.

### Sécurité

`SecurityFilterChain` : CSRF off pour API stateless, toutes les routes `/api/v1/**` `permitAll` pour ce cycle. Annoter les handlers `@PreAuthorize("permitAll")` pour ancrer la convention.

### Erreurs

| Situation | HTTP | Code |
|---|---|---|
| bbox invalide | 400 | `PROPERTY_BBOX_INVALID` |
| type/confidence hors enum | 400 | `PROPERTY_FILTER_INVALID` |
| page/size invalides | 400 | `PROPERTY_PAGE_INVALID` |
| id inconnu / deleted | 404 | `PROPERTY_NOT_FOUND` |
| autre | 500 | `INTERNAL_ERROR` |

Étendre `ApiExceptionHandler` avec une exception métier typée (ex. `ApiException(code, status)`) sans casser le handler health/`DataAccessException`.

Clés i18n front dans `messages/{pt,fr,en}/errors.json` (rédiger d’abord le portugais).

## Packages Java

```text
com.immodata.gb.domain.common.BaseEntity
com.immodata.gb.domain.organisation.*
com.immodata.gb.domain.property.*
com.immodata.gb.application.property.PropertyQueryService
com.immodata.gb.application.property.PropertyMapper
com.immodata.gb.infrastructure.persistence.property.PropertySpatialRepository
com.immodata.gb.api.PageResponse
com.immodata.gb.api.property.PropertyController
com.immodata.gb.api.property.PropertyMapItemDto
com.immodata.gb.infrastructure.security.SecurityConfig
```

Le health Bloc 0 reste inchangé dans son comportement. Ajouts `pom.xml` / config JPA (`ddl-auto=validate` ou `none` + Flyway) sans casser JDBC health existant (JPA et DataSource partagés).

## Frontend

### Route

`app/[locale]/map/page.tsx` — layout : `FilterPanel` + zone carte (`PropertyMap`) + `PropertyList`. Lien depuis la page d’accueil cycle 0 (ajout minimal d’un lien « Mapa »).

### URL

`/[locale]/map?bbox=w,s,e,n&type=&confidence=&page=`

- Absence de `bbox` → constante Bissau.
- Écriture `bbox` après `moveend` / `zoomend` (debounce ~300 ms) via `router.replace` (sans empiler l’historique à chaque pan).
- Filtres reset `page=0`.

### Carte

- MapLibre + react-map-gl, style raster OSM public (URL tuiles documentée ; attribution OSM obligatoire).
- Marqueurs pour chaque item de la page courante (pas de clustering).
- Popup (`PropertyPopup`) : titre, type, `ConfidenceBadge`, summary.
- Clic liste : `flyTo` + ouvrir popup / highlight.

### Données

- TanStack Query v5 : clé `['properties', bbox, type, confidence, page]`, debounce filtres/bbox ~300 ms.
- Client fetch vers `NEXT_PUBLIC_API_URL`.
- TypeScript strict, aucun `any`.

### Confiance UI

Badge : rouge 0–33, orange 34–66, vert 67–100, toujours avec le texte `confidenceSummary`.

### États

- Loading : skeleton liste ; carte déjà montée.
- Erreur : bandeau traduit (`errorCode`).
- Vide : message i18n « aucun bien dans l’emprise ».

## Tests

### Backend

- Unitaires : validation bbox/filtres ; mapper score→DTO.
- IT Testcontainers PostGIS : seed dans bbox ; hors bbox vide ; filtre `confidence=high` ; soft-delete exclu ; `{id}` 404 ; `size>100` → 400.

### Frontend

- Vitest : helpers URL bbox/filtres ; `ConfidenceBadge` classes/seuils.
- Contrôle manuel README : marqueurs + liste + popup + URL partageable. Pas de Playwright obligatoire dans ce cycle.

## Critères d’acceptation

- [ ] Carte OSM + marqueurs seed Bissau
- [ ] Liste synchronisée avec sélection / popup
- [ ] Filtres `type` / `confidence` + `bbox` dans l’URL
- [ ] Debounce refetch ~300 ms
- [ ] PostGIS `ST_Within` + soft delete
- [ ] Badge confiance + summary
- [ ] Endpoints publics sans JWT
- [ ] Pas de clusters / Martin biens / fiche complète

## Points à vérifier à la génération

- Version exacte `react-map-gl` / `maplibre-gl` compatible Next 14.2 + React 18 (literals dans `package.json`).
- Import CSS MapLibre dans le layout carte (éviter SSR cassé : composant carte `dynamic(..., { ssr: false })` si besoin).
- Ports de secours 8081 / 3010 si 8080 / 3000 occupés (déjà notés cycle 0) — README Bloc 1 renvoie aux mêmes consignes.
- UUID fixes du seed pour assertions IT stables.

