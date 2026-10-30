# Bloc 1 — Cartographie interactive — Design

Date : 2026-10-30  
Statut : en relecture (révision 2)  
Périmètre : preuve verticale mince (approche A) — carte + liste + bbox PostGIS, sans authentification  
Dépend de : Cycle 0 — Fondations (révision 2)

## Décisions

- Approche **A** : markers lon/lat via REST paginé + liste synchronisée. Pas de tuiles Martin pour les biens, pas de clusters, pas de polygones, pas de limites administratives.
- **Pas de Spring Security dans ce bloc.** Il n'y a ni JWT ni chaîne de sécurité : les endpoints carte sont publics parce qu'aucune sécurité n'existe encore. Spring Security, `@PreAuthorize` et les rôles arrivent avec le bloc identité (voir « Notes pour le bloc identité »).
- **Accès aux données avec `JdbcClient`** (Spring Boot 3.3), SQL natif PostGIS. Pas de JPA, pas de MapStruct, pas de Bean Validation dans ce bloc. JPA est envisagé au Bloc 2 (CRUD). Aucune nouvelle dépendance backend : `JdbcClient` est fourni par `spring-boot-starter-jdbc`, déjà présent.
- Confiance **figée en colonnes** sur `property` (`confidence_score`, `confidence_level`, `confidence_summary`). Le moteur configurable (Bloc 3) est hors scope.
- Fiche complète / CRUD / sources / documents = Bloc 2. Le clic marqueur ouvre une **popup** (résumé), pas une navigation fiche.
- Requête bbox : `geom && ST_MakeEnvelope(...)` sur index GIST. L'opérateur `&&` inclut les points situés sur le bord de l'enveloppe, contrairement à `ST_Within`.
- **Seed de 24 biens**, exact et déterministe (voir « Seed »), pour exercer la pagination : 20 biens en page 0, 4 en page 1.
- Le seed vit dans un dossier séparé, activé seulement par le profil `dev` : il ne s'applique pas en production.
- Filtres URL de ce bloc : `bbox`, `type`, `confidence`, `page` (et `size` côté API seulement). Le filtre `status` / `commercial_status` du PRD est reporté (colonne absente du schéma mince).
- Soft delete : `deleted_at TIMESTAMPTZ NULL` (pas de suppression physique). Les requêtes excluent `deleted_at IS NOT NULL`.
- Géométrie dans une table dédiée `property_geometry`, SRID 4326, type `POINT` pour ce bloc (`geom_type = 'point'`). Jamais de lat/lng seuls en base.
- Emprise Bissau par défaut (constante front + seed) : `bbox=-15.70,11.80,-15.50,11.95` (w,s,e,n). Bissau est à environ 11,86° N et 15,6° O : la latitude est **positive**.
- Pas de filtre par organisation dans ce bloc (endpoints publics). Le DTO expose `organisationId` ; ce choix est revu au bloc identité.
- Dépendances nouvelles frontend : `maplibre-gl` et `react-map-gl` (import `react-map-gl/maplibre`), en versions exactes et compatibles entre elles, avec Next 14.2 et React 18. Pas de `zustand` : état local React + URL.

## Critère de succès

1. Compose + backend en profil `dev` : Flyway applique `V2`–`V4` et le seed. La base contient 1 organisation, 24 biens et 24 géométries.
2. `GET /api/v1/properties?bbox=-15.70,11.80,-15.50,11.95` renvoie HTTP 200, enveloppe `ApiResponse` + `PageResponse`, avec `page = 0`, `size = 20`, `totalElements = 24`, `totalPages = 2` et 20 items (de `BIS-024` à `BIS-005`). Avec `page=1`, la réponse contient 4 items (de `BIS-004` à `BIS-001`).
3. `http://localhost:3000/pt/map` (ou port de secours documenté) affiche fond OSM, marqueurs de la page courante, liste synchronisée, popup au clic, badge de confiance + summary.
4. Changer les filtres ou faire un pan/zoom met à jour l'URL ; coller l'URL restaure la vue, les filtres et la page.
5. `./mvnw verify` et `npm test` passent, y compris les tests du cycle 0, inchangés.

## Hors de ce bloc

Spring Security, JWT et rôles métier, JPA, MapStruct, Bean Validation, filtre par organisation, clusters, Martin MVT biens, polygones, `admin_boundary`, couches SIG, fiche détail navigable, CRUD, sources, documents, estimation, moteur de confiance, Playwright (non requis pour ce bloc), import CSV, saisie terrain, back-office.

Le catalogue Martin peut rester vide.

## Architecture

```text
Navigateur /[locale]/map?bbox&type&confidence&page
  → Next.js (CSR carte)
  → GET {NEXT_PUBLIC_API_URL}/api/v1/properties?...
  → PropertyController
  → PropertyRequestParser (validation)
  → PropertyQueryService + PropertySpatialRepository (JdbcClient, PostGIS)
  → PageResponse<PropertyMapItem>
  → marqueurs MapLibre + liste

Clic marqueur | clic liste
  → sélection locale + popup depuis l'item déjà chargé (pas de fetch supplémentaire)
```

Le détail mince `GET /properties/{id}` existe pour le contrat API et les tests 404. L'UI carte/liste **n'appelle pas** cet endpoint dans ce bloc.

## Modèle de données (mince)

Les migrations `V2`–`V4` sont dans `db/migration`. Le seed est dans `db/seed` (voir « Seed »).

### `V2__create_organisation.sql`

Table `organisation` : `id` UUID PK, `name` VARCHAR NOT NULL, `created_at` et `updated_at` TIMESTAMPTZ NOT NULL DEFAULT `now()`, `deleted_at` TIMESTAMPTZ NULL. Suffisant pour `organisation_id` tenant.

### `V3__create_property.sql`

Table `property` (sous-ensemble PRD) :

| Colonne | Notes |
|---|---|
| `id` UUID PK | `gen_random_uuid()` |
| `organisation_id` UUID NOT NULL | FK organisation |
| `reference` VARCHAR(50) NOT NULL | unicité : index unique partiel `WHERE deleted_at IS NULL` |
| `title` VARCHAR(500) NOT NULL | |
| `property_type` VARCHAR(50) NOT NULL | `house` \| `apartment` \| `land` \| `commercial` (CHECK) |
| `confidence_score` SMALLINT NOT NULL | 0–100 |
| `confidence_level` VARCHAR(10) NOT NULL | `low` \| `medium` \| `high` |
| `confidence_summary` TEXT NOT NULL | obligatoire |
| `created_at` / `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `now()` |
| `created_by` / `updated_by` | UUID NULL (pas de FK users) |
| `deleted_at` | TIMESTAMPTZ NULL |

Contrainte CHECK qui lie le niveau au score, avec les seuils uniques du bloc :

```sql
CHECK (
  (confidence_level = 'low'    AND confidence_score BETWEEN 0  AND 33) OR
  (confidence_level = 'medium' AND confidence_score BETWEEN 34 AND 66) OR
  (confidence_level = 'high'   AND confidence_score BETWEEN 67 AND 100)
)
```

Index partiels, tous `WHERE deleted_at IS NULL` : `(organisation_id)`, `(property_type)`, `(confidence_level)`, `(created_at DESC, id)`.

Pas de `admin_boundary_id`, prix, sources, ni `is_deleted` booléen séparé : le soft delete est `deleted_at`.

### `V4__create_property_geometry.sql`

| Colonne | Notes |
|---|---|
| `id` UUID PK | |
| `property_id` UUID UNIQUE NOT NULL | FK property, 1 géométrie affichable pour ce bloc |
| `geom` geometry(Point, 4326) NOT NULL | |
| `geom_type` VARCHAR(20) NOT NULL | CHECK `= 'point'` |
| `precision_level` VARCHAR(20) NULL | CHECK dans `gps` \| `address` \| `zone` |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `now()` |

Index GIST sur `geom`. Le soft delete se lit sur `property.deleted_at` : la table de géométrie n'a pas de `deleted_at`.

### Seed

Fichier `src/main/resources/db/seed/R__seed_bissau_dev.sql`, migration répétable et idempotente (`INSERT ... ON CONFLICT (id) DO NOTHING`). Le seed est chargé uniquement par le profil `dev` : `application-dev.yml` déclare `spring.flyway.locations: classpath:db/migration,classpath:db/seed`. Les tests d'intégration héritent du profil `dev` par défaut (donc du seed) et remplacent seulement la `DataSource` par Testcontainers. Pour repartir d'un seed modifié sur une base existante : `docker compose down -v`.

Contenu, exact :

- 1 organisation « ImmoData Bissau Pilote », UUID `00000000-0000-0000-0000-00000000000a`.
- 24 biens numérotés `n = 1..24`, références `BIS-001` à `BIS-024`, UUID `00000000-0000-0000-0001-0000000000NN` (NN = `n` sur deux chiffres). Les géométries ont les UUID `00000000-0000-0000-0002-0000000000NN`. Ces UUID sont documentés en tête du fichier et servent aux assertions des tests.
- `created_at` = `2026-10-01 09:00:00+00` + `n` minutes : `BIS-024` est le plus récent, ordre déterministe.
- Type : `[house, apartment, land, commercial][(n-1) % 4]`.
- Niveau : `[low, medium, high][((n-1) / 4) % 3]` (division entière). Chaque couple (type, niveau) apparaît exactement deux fois.
- Score : `low` → 12 (si `n ≤ 12`) ou 28 ; `medium` → 41 ou 58 ; `high` → 72 ou 91. Le premier score est pour `n ≤ 12`, le second pour `n ≥ 13`.
- Résultat attendu : 6 biens par type, 8 par niveau, 2 par couple (type, niveau).
- Coordonnées : colonne `i = (n-1) % 6`, ligne `j = (n-1) / 6`. Longitude `-15.68 + 0.032 × i` (de -15.68 à -15.52), latitude `11.82 + 0.036 × j` (de 11.82 à 11.928). Tous les points sont strictement à l'intérieur de l'emprise par défaut.
- `precision_level` : `gps` pour `n` impair, `address` pour `n` pair.
- `title` et `confidence_summary` non vides, rédigés en portugais.

## API

### Contrats partagés

- `ApiResponse<T>` du cycle 0.
- Nouveau `PageResponse<T>` : `page` (0-based), `size`, `totalElements`, `totalPages`, `items` (liste). Il est placé dans `com.immodata.gb.api` ; le service applicatif le renvoie directement, dépendance acceptée pour ce bloc.
- Défauts : `page=0`, `size=20`, `size` max `100`. `totalPages = ceil(totalElements / size)`, soit 0 si aucun résultat. Une page au-delà de la dernière renvoie HTTP 200 avec `items` vide.

### `GET /api/v1/properties`

Query :

| Param | Règle |
|---|---|
| `bbox` | **obligatoire** `w,s,e,n` : exactement 4 nombres finis séparés par des virgules, `-180 ≤ w,e ≤ 180`, `-90 ≤ s,n ≤ 90`, `w < e`, `s < n`. L'antiméridien n'est pas supporté. |
| `type` | optionnel ; un de `house\|apartment\|land\|commercial`. Valeur vide = ignoré. Paramètre répété = erreur. |
| `confidence` | optionnel ; un de `low\|medium\|high` (filtre sur **level**, pas sur le score). Valeur vide = ignoré. Paramètre répété = erreur. |
| `page` | entier ≥ 0 |
| `size` | entier de 1 à 100 |

Le contrôleur reçoit les paramètres bruts (`MultiValueMap<String, String>`) et les confie à `PropertyRequestParser`, une classe utilitaire statique. Les erreurs de validation sont donc des `ApiException` avec les codes métier du bloc, et non des exceptions Spring MVC. Les paramètres inconnus sont ignorés.

Réponse `data` : `PageResponse<PropertyMapItem>`.

`PropertyMapItem` (record, utilisé directement comme contrat JSON) : `id`, `organisationId`, `reference`, `title`, `propertyType`, `confidenceScore`, `confidenceLevel`, `confidenceSummary`, `longitude`, `latitude`. Les coordonnées sont des champs plats (ordre lon, lat pour MapLibre), pas un objet GeoJSON.

Exemple, HTTP 200 :

```json
{
  "success": true,
  "data": {
    "page": 0,
    "size": 20,
    "totalElements": 24,
    "totalPages": 2,
    "items": [
      {
        "id": "00000000-0000-0000-0001-000000000024",
        "organisationId": "00000000-0000-0000-0000-00000000000a",
        "reference": "BIS-024",
        "title": "…",
        "propertyType": "commercial",
        "confidenceScore": 91,
        "confidenceLevel": "high",
        "confidenceSummary": "…",
        "longitude": -15.52,
        "latitude": 11.928
      }
    ]
  },
  "message": null,
  "meta": {}
}
```

Requête (`JdbcClient`, paramètres nommés, jamais de concaténation de valeurs) :

```sql
SELECT p.id, p.organisation_id, p.reference, p.title, p.property_type,
       p.confidence_score, p.confidence_level, p.confidence_summary,
       ST_X(g.geom) AS longitude, ST_Y(g.geom) AS latitude
FROM property p
JOIN property_geometry g ON g.property_id = p.id
WHERE p.deleted_at IS NULL
  AND g.geom && ST_MakeEnvelope(:w, :s, :e, :n, 4326)
  [AND p.property_type = :type]
  [AND p.confidence_level = :confidence]
ORDER BY p.created_at DESC, p.id
LIMIT :size OFFSET :offset
```

Les clauses entre crochets sont ajoutées dynamiquement seulement quand le filtre est présent (pas de `:x IS NULL OR`). Une seconde requête `SELECT count(*)` applique exactement les mêmes conditions pour `totalElements`.

### `GET /api/v1/properties/{id}`

Même contrat `PropertyMapItem`. L'identifiant est lu comme texte puis converti en UUID par le contrôleur : valeur non UUID → 400 `PROPERTY_ID_INVALID`. 404 `PROPERTY_NOT_FOUND` si absent ou soft-deleted.

### Sécurité

Aucune dans ce bloc : pas de Spring Security, pas de filtre d'authentification. Les routes `/api/v1/**` sont publiques de fait. Le `CorsFilter` du cycle 0 reste en place sans changement, et les `@WebMvcTest` du cycle 0 restent valides.

### Erreurs

| Situation | HTTP | Code (`message`) |
|---|---|---|
| bbox absente ou invalide | 400 | `PROPERTY_BBOX_INVALID` |
| type/confidence hors enum ou répétés | 400 | `PROPERTY_FILTER_INVALID` |
| page/size invalides | 400 | `PROPERTY_PAGE_INVALID` |
| id non UUID | 400 | `PROPERTY_ID_INVALID` |
| id inconnu / soft-deleted | 404 | `PROPERTY_NOT_FOUND` |
| route inconnue | 404 | `NOT_FOUND` |
| méthode HTTP non supportée | 405 | `METHOD_NOT_ALLOWED` |
| `DataAccessException` (base inaccessible) | 503 | `HEALTH_DATABASE_UNAVAILABLE` |
| autre | 500 | `INTERNAL_ERROR` |

Précision sur la ligne `DataAccessException` : le handler du cycle 0 est conservé tel quel, donc une panne de base sur `/api/v1/properties` renvoie le même 503 que le health, avec le même corps (`data.database = "down"`). L'UI de la carte affiche alors la phrase `errors.HEALTH_DATABASE_UNAVAILABLE` déjà traduite au cycle 0. Ce comportement est testé.

Implémentation : `ApiException(code, status)` dans `api/error`. `ApiExceptionHandler` ajoute des handlers pour `ApiException`, pour `NoResourceFoundException` (404 `NOT_FOUND`) et pour `HttpRequestMethodNotSupportedException` (405 `METHOD_NOT_ALLOWED`). Le handler catch-all du cycle 0 reste le seul à produire `INTERNAL_ERROR`. Le comportement du health reste inchangé.

Pour les codes inconnus du frontend (`NOT_FOUND`, `METHOD_NOT_ALLOWED`), l'UI affiche `common.error`.

## Packages Java

```text
com.immodata.gb.domain.property.PropertyType
com.immodata.gb.domain.property.ConfidenceLevel
com.immodata.gb.application.property.BoundingBox
com.immodata.gb.application.property.PropertyQuery
com.immodata.gb.application.property.PropertyMapItem
com.immodata.gb.application.property.PropertyQueryService
com.immodata.gb.infrastructure.persistence.property.PropertySpatialRepository
com.immodata.gb.api.PageResponse
com.immodata.gb.api.error.ApiException
com.immodata.gb.api.property.PropertyController
com.immodata.gb.api.property.PropertyRequestParser
```

`PropertyType` et `ConfidenceLevel` sont des enums portant la valeur SQL (`house`, `low`, etc.). `PropertyQuery` est un record validé (`BoundingBox`, `type`, `confidence`, `page`, `size`). Pas de `BaseEntity`, pas d'entité JPA, pas de package `organisation` : la table `organisation` n'a pas de code Java dans ce bloc.

Le health du cycle 0 reste inchangé. `JdbcClient` partage la `DataSource` existante.

## Frontend

### Route et fichiers

`app/[locale]/map/page.tsx` : composant serveur, rend `<Suspense>` autour de `MapView` (nécessaire pour `useSearchParams` avec Next 14).

Fichiers :

- `components/map/MapView.tsx` : composant client, orchestre URL, requête, sélection. Charge `PropertyMap` avec `dynamic(() => import('./PropertyMap'), { ssr: false })` (obligatoire : MapLibre exige `window` et WebGL).
- `components/map/PropertyMap.tsx` : carte et marqueurs. Importe `maplibre-gl/dist/maplibre-gl.css`.
- `components/map/FilterPanel.tsx`, `components/map/PropertyList.tsx`, `components/map/PropertyPopup.tsx`, `components/map/ConfidenceBadge.tsx`
- `lib/map/constants.ts` : `BISSAU_BBOX` (`-15.70,11.80,-15.50,11.95`), style raster OSM
- `lib/map/url-state.ts` : lecture et écriture de l'état URL
- `lib/api/http.ts` : lecture JSON commune (corps lu quel que soit le statut, erreur seulement sur réseau/CORS/non JSON), utilisée par `lib/api/health.ts` (refactor sans changement de comportement) et `lib/api/properties.ts`
- `lib/api/types.ts` : ajout de `PageResponse<T>` et `PropertyMapItem`
- `messages/{pt,fr,en}/map.json` : nouveau fichier de messages, fusionné par `i18n/request.ts` avec `common.json` et `errors.json`

Le layout affiche désormais, à côté du `LocaleSwitcher`, un lien vers la carte (`common.nav.map`) en utilisant le `Link` de `i18n/navigation.ts`. C'est le seul changement au layout du cycle 0.

Le `LocaleSwitcher` conserve le chemin **et la query string** : changer de langue sur `/map` ne doit perdre ni la bbox ni les filtres.

### URL

`/[locale]/map?bbox=w,s,e,n&type=&confidence=&page=`

- `bbox` absente ou invalide → constante Bissau. `type` ou `confidence` invalide → ignoré. `page` invalide → 0.
- Sérialisation : bbox arrondie à 5 décimales, filtres vides omis, `page` omis si 0.
- La bbox écrite est toujours ramenée dans les plages valides (longitude [-180, 180], latitude [-90, 90]) : MapLibre peut renvoyer des longitudes hors plage à bas zoom.
- Écriture via `router.replace(url, { scroll: false })` (pas d'empilement d'historique à chaque pan).
- `page` repasse à 0 quand la bbox **ou** un filtre change.
- Si la page est vide alors que `totalElements > 0` et `page > 0` (URL collée périmée), la page est remise à 0.

### Source de vérité

- Au montage, l'URL initialise la vue : `bounds` de la bbox, padding 0. La carte est ensuite **non contrôlée** : elle ne réagit plus aux changements d'URL.
- Le premier `moveend` du chargement est ignoré.
- Chaque `moveend` suivant (action de l'utilisateur ou déplacement programmatique) écrit la bbox visible dans l'URL, avec un **debounce unique d'environ 300 ms**. La requête lit l'URL ; elle n'a pas de second debounce.
- La bbox visible peut être plus grande que celle de l'URL à cause du ratio de l'écran : restaurer une URL garantit que la zone demandée est visible, pas qu'elle est reproduite au pixel près.

### Carte

- MapLibre + `react-map-gl/maplibre`, style raster OSM public : source `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, `tileSize` 256, `maxzoom` 19, attribution « © OpenStreetMap contributors » obligatoire. La politique d'usage des tuiles publiques OSM est restrictive : acceptable en développement, à remplacer par un fournisseur avant production.
- Marqueurs pour chaque item de la **page courante** (pas de clustering), colorés selon `confidenceLevel` avec la même palette que le badge.
- Popup (`PropertyPopup`) : titre, référence, type, `ConfidenceBadge`, `confidenceSummary`.
- Sélection : l'item sélectionné est stocké en entier dans l'état local (pas seulement son `id`). Il reste sélectionné, popup ouverte, même si un refetch le retire de la page courante. Il est désélectionné par clic sur le fond de carte, par fermeture de la popup, ou par `Escape`.
- Clic dans la liste : sélectionne l'item, puis `easeTo` centré sur le point **seulement si le point est hors de la vue** (`map.getBounds().contains`). Aucun `flyTo` systématique.

### Données

- TanStack Query v5, clé `['properties', bbox, type, confidence, page]`, lue depuis l'URL.
- Options : `placeholderData: keepPreviousData` (la liste et les marqueurs ne clignotent pas pendant un pan), `retry: false`, `refetchOnWindowFocus: false`. Le `signal` de la requête est transmis à `fetch` pour annuler les requêtes périmées.
- TypeScript strict, aucun `any`.

### Liste et pagination

- Liste de la page courante, éléments rendus comme boutons focusables, `confidenceSummary` tronqué sur 2 lignes (`line-clamp`).
- Texte de plage : `map.list.range` (« 1–20 sur 24 »), puis boutons Précédent et Suivant qui modifient `page` dans l'URL ; désactivés aux extrémités.
- La bbox des marqueurs et de la liste est la même : la page courante.

### Confiance UI

Le badge dépend de `confidenceLevel` (rouge `low`, orange `medium`, vert `high`) et affiche toujours le libellé du niveau, le score `n/100` et, dans la popup et la liste, le texte `confidenceSummary`. La couleur n'est jamais le seul indicateur. Les seuils 0–33 / 34–66 / 67–100 vivent uniquement dans la contrainte CHECK de la base et dans le seed ; le front ne recalcule rien.

### États

- Chargement : skeleton de liste au premier chargement seulement ; la carte est déjà montée.
- Erreur : bandeau traduit si le corps JSON porte un code connu (`errors.PROPERTY_BBOX_INVALID`, `errors.PROPERTY_FILTER_INVALID`, `errors.PROPERTY_PAGE_INVALID`, `errors.HEALTH_DATABASE_UNAVAILABLE`, `errors.INTERNAL_ERROR`) ; `common.error` pour tout autre cas (réseau, CORS, corps non JSON, code inconnu). Les données précédentes restent affichées sous le bandeau.
- Vide : `map.list.empty`.

### Clés i18n (à rédiger d'abord en portugais)

| Clé | Portugais | Français | Anglais |
|---|---|---|---|
| `common.nav.map` | Mapa | Carte | Map |
| `map.title` | Mapa de imóveis | Carte des biens | Property map |
| `map.filters.type` | Tipo | Type | Type |
| `map.filters.confidence` | Confiança | Confiance | Confidence |
| `map.filters.all` | Todos | Tous | All |
| `map.type.house` | Moradia | Maison | House |
| `map.type.apartment` | Apartamento | Appartement | Apartment |
| `map.type.land` | Terreno | Terrain | Land |
| `map.type.commercial` | Comercial | Local commercial | Commercial |
| `map.confidence.low` | Baixa | Faible | Low |
| `map.confidence.medium` | Média | Moyenne | Medium |
| `map.confidence.high` | Alta | Élevée | High |
| `map.list.title` | Imóveis | Biens | Properties |
| `map.list.empty` | Nenhum imóvel nesta área. | Aucun bien dans l'emprise. | No properties in this area. |
| `map.list.range` | {from}–{to} de {total} | {from}–{to} sur {total} | {from}–{to} of {total} |
| `map.list.previous` | Anterior | Précédent | Previous |
| `map.list.next` | Seguinte | Suivant | Next |
| `errors.PROPERTY_BBOX_INVALID` | A área do mapa é inválida. | La zone de la carte est invalide. | The map area is invalid. |
| `errors.PROPERTY_FILTER_INVALID` | Filtro inválido. | Filtre invalide. | Invalid filter. |
| `errors.PROPERTY_PAGE_INVALID` | Página inválida. | Page invalide. | Invalid page. |

`PROPERTY_NOT_FOUND` et `PROPERTY_ID_INVALID` n'ont pas de clé : l'UI n'appelle pas le détail dans ce bloc.

## Tests

### Backend

Unitaires, `PropertyRequestParser` :

- bbox valide ; absente ; non numérique ; moins ou plus de 4 valeurs ; `NaN` / infinie ; hors plage ; `w >= e` ; `s >= n` ;
- `type` et `confidence` : valeurs valides, vide ignoré, hors enum, paramètre répété ;
- `page` négative ou non numérique, `size` à 0, 101, non numérique ; défauts `page=0`, `size=20` ;
- `{id}` UUID valide et non valide.

`@WebMvcTest` sur `PropertyController` avec `ApiExceptionHandler` importé et `PropertyQueryService` simulé :

- codes d'erreur 400 mappés avec le bon `message` ;
- 404 `PROPERTY_NOT_FOUND` ; 500 `INTERNAL_ERROR` sur `RuntimeException` ; 503 `HEALTH_DATABASE_UNAVAILABLE` sur `DataAccessException` ;
- route inconnue : 404 `NOT_FOUND` ; méthode `POST` : 405 `METHOD_NOT_ALLOWED` ;
- forme JSON de l'enveloppe.

Intégration, Testcontainers PostGIS (profil `dev` hérité, donc seed chargé) :

- Flyway applique `V2`–`V4` et le seed : 1 organisation, 24 biens, 24 géométries ; tous les points ont une latitude entre 11 et 12 et une longitude entre -16 et -15 ;
- bbox par défaut : `totalElements = 24`, `totalPages = 2`, page 0 = 20 items de `BIS-024` à `BIS-005` ; page 1 = 4 items de `BIS-004` à `BIS-001` ; aucune référence en double entre les deux pages ;
- `size=100` : 24 items ; `size=101` : 400 `PROPERTY_PAGE_INVALID` ;
- bbox sans résultat (`-15.40,11.80,-15.30,11.95`) : 200, `items` vide, `totalElements = 0`, `totalPages = 0` ;
- sous-bbox `-15.70,11.80,-15.60,11.95` : 12 biens ; bbox dont le bord est sur une colonne de points (`-15.70,11.80,-15.616,11.95`) : 12 biens (les points du bord sont inclus) ;
- `type=house` : 6 biens ; `confidence=high` : 8 ; `type=house&confidence=high` : 2 ;
- soft delete : un bien supplémentaire avec géométrie dans l'emprise, inséré par le test avec `deleted_at` renseigné dans une transaction annulée en fin de test, est absent de la liste et renvoie 404 sur `{id}` ;
- `{id}` connu (UUID du seed) : 200 ; inconnu : 404 `PROPERTY_NOT_FOUND` ;
- le CHECK de confiance rejette un couple niveau/score incohérent.

Les tests du cycle 0 (health) passent sans modification.

### Frontend

Vitest et Testing Library :

- `lib/map/url-state.ts` : lecture (valeurs absentes, invalides, bbox par défaut), sérialisation (arrondi à 5 décimales, omissions), remise à 0 de `page`, bbox ramenée dans les plages valides ;
- `ConfidenceBadge` : libellé, score et classe de couleur pour chaque niveau ;
- `PropertyList` : rendu des items, état vide, texte de plage, boutons Précédent/Suivant désactivés aux extrémités, appel du callback au clic ;
- `lib/api/properties.ts` : corps 400 avec code connu renvoyé tel quel, échec réseau et corps non JSON lèvent une erreur ;
- `LocaleSwitcher` (test du cycle 0 étendu) : la query string est conservée ;
- test de parité des clés, étendu à `map.json`.

`PropertyMap` n'est pas testé unitairement : WebGL est absent de jsdom. Il est couvert par le contrôle manuel. Pas de Playwright dans ce bloc.

### Contrôle manuel (README)

- `docker compose up -d --wait`, backend, frontend, puis `http://localhost:3000/pt/map` ;
- 20 marqueurs et la liste « 1–20 de 24 » ; le bouton Suivant affiche « 21–24 de 24 » ;
- clic sur un marqueur : popup avec badge et summary ; clic dans la liste : la popup s'ouvre, la carte ne bouge que si le point est hors vue ;
- changer un filtre : l'URL change et la page repasse à 0 ; pan/zoom : la bbox dans l'URL change ;
- coller l'URL dans un autre onglet : mêmes vue, filtres et page ;
- changer de langue sur `/map` : la query string est conservée ;
- `curl "http://localhost:8080/api/v1/properties?bbox=-15.70,11.80,-15.50,11.95"` renvoie la page 0 attendue.

## Critères d'acceptation

- [ ] Carte OSM + 20 marqueurs seed Bissau en page 0, 4 en page 1
- [ ] Liste synchronisée avec sélection / popup, texte de plage et pagination
- [ ] Filtres `type` / `confidence`, `bbox` et `page` dans l'URL, restauration par URL collée
- [ ] Debounce unique d'environ 300 ms à l'écriture de l'URL
- [ ] `geom && ST_MakeEnvelope`, soft delete et tri stable `created_at DESC, id`
- [ ] Badge de confiance (libellé + score) + summary, CHECK niveau/score en base
- [ ] Endpoints publics, sans Spring Security
- [ ] Seed dev uniquement (`db/seed`), absent des migrations de production
- [ ] Pas de clusters / Martin biens / fiche complète
- [ ] Tests du cycle 0 inchangés et verts

## Points à vérifier à la génération

- Versions exactes de `react-map-gl` et `maplibre-gl`, compatibles entre elles et avec Next 14.2 et React 18, écrites en littéral dans `package.json` ; chemin d'import `react-map-gl/maplibre`.
- `dynamic(..., { ssr: false })` appelé depuis un composant client (interdit dans un composant serveur avec Next 14), et import du CSS MapLibre.
- `JdbcClient` disponible et auto-configuré avec la version de Spring Boot 3.3 résolue au cycle 0.
- Comportement de Flyway avec une migration répétable dans `db/seed` et la propriété `spring.flyway.locations` du profil `dev`.
- Ports de secours 8081 / 3010 si 8080 / 3000 sont occupés (déjà notés au cycle 0) ; le README du Bloc 1 renvoie aux mêmes consignes.

## Notes pour le bloc identité

Quand Spring Security sera introduit :

- la chaîne doit être stateless, CSRF désactivé pour l'API, `formLogin` et `httpBasic` désactivés, `UserDetailsServiceAutoConfiguration` exclue, `@EnableMethodSecurity` activé ;
- `/api/v1/health` reste public ;
- le `CorsFilter` doit rester à priorité maximale (ou être intégré à la chaîne) pour que les 401/403 portent l'en-tête CORS ;
- les `@WebMvcTest` existants devront importer la configuration de sécurité, sinon la sécurité par défaut renverra 401 ;
- les requêtes de `PropertySpatialRepository` recevront un filtre par organisation, et l'exposition de `organisationId` dans le DTO sera réévaluée.

## Changements de la révision 2

- **Correction de la bbox Bissau** : latitude positive (`11.80`–`11.95`) partout, avec un test de plausibilité sur le seed.
- **Spring Security reporté** au bloc identité (votre choix) : plus de `SecurityConfig` ni de `@PreAuthorize`, tests du cycle 0 inchangés ; notes de transition ajoutées.
- **`JdbcClient`** à la place de JPA, MapStruct et Bean Validation (votre choix) : aucune nouvelle dépendance backend, validation manuelle par `PropertyRequestParser`.
- **Seed de 24 biens** (votre choix), déterministe et documenté, dans `db/seed` en migration répétable, chargé par le profil `dev` seulement.
- Tri stable (`created_at DESC, id`), `&&` à la place de `ST_Within`, `CHECK` niveau/score, index partiels et unicité de `reference` partielle.
- Validation détaillée de la bbox et des filtres, nouveaux codes `PROPERTY_ID_INVALID`, `NOT_FOUND` et `METHOD_NOT_ALLOWED`, handlers pour que les erreurs Spring MVC ne deviennent pas des 500.
- Frontend : source de vérité URL/carte, debounce unique, `keepPreviousData`, annulation par `signal`, sélection conservée, pagination dans l'UI, `Suspense` et `ssr: false`, `LocaleSwitcher` qui conserve la query string, clés i18n listées dans un nouveau `map.json`.
- Tests complétés : pagination et unicité entre pages, bord de bbox, soft delete, CHECK, erreurs 404/405/500, URL, liste, client API.
