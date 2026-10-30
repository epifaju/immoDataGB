# Bloc 1 — Cartographie interactive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exposer une API spatiale PostGIS paginée et une page `/[locale]/map` (carte MapLibre + liste synchronisée + URL partageable) alimentée par un seed Bissau de 24 biens.

**Architecture:** Migrations Flyway V2–V4 (schéma) + seed répétable `db/seed` (profil `dev`). Backend JDBC/`JdbcClient` sans JPA ni Spring Security : `PropertyRequestParser` → `PropertyQueryService` → `PropertySpatialRepository` (`geom && ST_MakeEnvelope`). Frontend Next 14 : URL comme source de vérité, TanStack Query, MapLibre via `dynamic(..., { ssr: false })`.

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, JdbcClient, PostGIS 3.4, Next.js 14.2.35, next-intl 3.26.5, TanStack Query 5.105.0, maplibre-gl `4.7.1`, react-map-gl `7.1.9`, Vitest 2.1.9.

**Spec:** `docs/superpowers/specs/2026-10-30-bloc1-cartographie-design.md` (révision 2).

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, clusters, Martin MVT biens, polygones, admin boundaries, fiche navigable, CRUD.
- Aucune nouvelle dépendance Maven. Frontend : seulement `maplibre-gl@4.7.1` et `react-map-gl@7.1.9` (littéraux ; import `react-map-gl/maplibre`).
- Soft delete : `deleted_at IS NULL`. Bbox défaut : `-15.70,11.80,-15.50,11.95` (latitude **positive**).
- Requête spatiale : `g.geom && ST_MakeEnvelope(:w,:s,:e,:n,4326)`. Tri : `created_at DESC, id`.
- `ApiResponse` : `success`, `data`, `message`, `meta` (cycle 0). Codes d'erreur dans `message`.
- `PageResponse` : `page`, `size`, `totalElements`, `totalPages`, `items`. Défauts `page=0`, `size=20`, max `size=100`.
- Seed uniquement profil `dev` : `spring.flyway.locations: classpath:db/migration,classpath:db/seed`.
- i18n : clés d'abord en portugais ; nouveau `messages/{pt,fr,en}/map.json`.
- Shell PowerShell. Backend : `.\backend\mvnw.cmd` depuis la racine ou `.\mvnw.cmd` depuis `backend/`. Commits : `git.exe commit -F` si `git commit` injecte `--trailer`.
- Ne pas modifier les migrations déjà appliquées (`V1`). Ne pas casser les tests health du cycle 0.
- Ce bloc diverge volontairement de `.cursorrules` (pas de JPA/MapStruct/`@PreAuthorize`) : la spec révision 2 prime.

Les commandes partent de la racine du dépôt sauf indication contraire.

---

## File map

**Backend — créer**
- `backend/src/main/resources/db/migration/V2__create_organisation.sql`
- `backend/src/main/resources/db/migration/V3__create_property.sql`
- `backend/src/main/resources/db/migration/V4__create_property_geometry.sql`
- `backend/src/main/resources/db/seed/R__seed_bissau_dev.sql`
- `domain/property/PropertyType.java`, `ConfidenceLevel.java`
- `application/property/BoundingBox.java`, `PropertyQuery.java`, `PropertyMapItem.java`, `PropertyQueryService.java`
- `infrastructure/persistence/property/PropertySpatialRepository.java`
- `api/PageResponse.java`, `api/error/ApiException.java`
- `api/property/PropertyController.java`, `PropertyRequestParser.java`
- Tests miroir sous `backend/src/test/java/...`

**Backend — modifier**
- `application-dev.yml` — `spring.flyway.locations`
- `api/error/ApiExceptionHandler.java` — handlers `ApiException`, 404, 405

**Frontend — créer**
- `app/[locale]/map/page.tsx`
- `components/map/{MapView,PropertyMap,FilterPanel,PropertyList,PropertyPopup,ConfidenceBadge}.tsx` (+ tests hors `PropertyMap`)
- `lib/map/constants.ts`, `lib/map/url-state.ts` (+ test)
- `lib/api/http.ts`, `lib/api/properties.ts` (+ test)
- `messages/{pt,fr,en}/map.json`

**Frontend — modifier**
- `package.json` — deps map
- `lib/api/types.ts`, `lib/api/health.ts` (refactor via `http.ts`)
- `i18n/request.ts`, `messages/*/common.json`, `messages/*/errors.json`
- `app/[locale]/layout.tsx`, `components/locale/LocaleSwitcher.tsx` (+ test)
- `README.md`

---

### Task 1: Schéma Flyway V2–V4

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_organisation.sql`
- Create: `backend/src/main/resources/db/migration/V3__create_property.sql`
- Create: `backend/src/main/resources/db/migration/V4__create_property_geometry.sql`

**Interfaces:**
- Consumes: `V1__enable_spatial_extensions.sql` (PostGIS déjà actif).
- Produces: tables `organisation`, `property`, `property_geometry` conformes à la spec § Modèle de données.

- [ ] **Step 1: Écrire `V2__create_organisation.sql`**

```sql
CREATE TABLE organisation (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ NULL
);
```

- [ ] **Step 2: Écrire `V3__create_property.sql`**

Inclure CHECK type, CHECK niveau↔score (seuils 0–33 / 34–66 / 67–100), FK `organisation`, index unique partiel sur `reference WHERE deleted_at IS NULL`, index partiels `(organisation_id)`, `(property_type)`, `(confidence_level)`, `(created_at DESC, id)` tous `WHERE deleted_at IS NULL`. Colonnes exactes : spec § V3.

- [ ] **Step 3: Écrire `V4__create_property_geometry.sql`**

`geom geometry(Point, 4326) NOT NULL`, `geom_type` CHECK `= 'point'`, `precision_level` CHECK IN (`gps`,`address`,`zone`) NULL, `property_id` UNIQUE NOT NULL FK, index GIST sur `geom`. Pas de `deleted_at`.

- [ ] **Step 4: Vérifier le SQL (Compose déjà up, ou le démarrer)**

```powershell
docker compose up -d --wait postgis
Get-Content backend\src\main\resources\db\migration\V2__create_organisation.sql, backend\src\main\resources\db\migration\V3__create_property.sql, backend\src\main\resources\db\migration\V4__create_property_geometry.sql | Select-String -Pattern "CREATE TABLE"
```

Expected: trois `CREATE TABLE`.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/resources/db/migration/V2__create_organisation.sql backend/src/main/resources/db/migration/V3__create_property.sql backend/src/main/resources/db/migration/V4__create_property_geometry.sql
# message: feat: add property schema migrations for map bloc
git.exe commit -F .git/COMMIT_MSG_TMP.txt
```

---

### Task 2: Seed dev + locations Flyway

**Files:**
- Create: `backend/src/main/resources/db/seed/R__seed_bissau_dev.sql`
- Modify: `backend/src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: tables V2–V4.
- Produces: 1 org UUID `00000000-0000-0000-0000-00000000000a`, 24 biens `BIS-001`…`BIS-024`, 24 géométries ; chargé seulement en profil `dev`.

- [ ] **Step 1: Ajouter les locations Flyway dans `application-dev.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5435/immodata
    username: immodata
    password: immodata
    hikari:
      connection-timeout: 3000
  flyway:
    locations: classpath:db/migration,classpath:db/seed

app:
  cors:
    allowed-origins: http://localhost:3000
```

- [ ] **Step 2: Écrire `R__seed_bissau_dev.sql`**

En-tête commenté listant les UUID. `INSERT ... ON CONFLICT (id) DO NOTHING` pour org, properties, geometries.

Règles (implémenter littéralement, n = 1..24) :
- property UUID `00000000-0000-0000-0001-0000000000NN`, geom UUID `...0002-0000000000NN`
- `created_at = '2026-10-01 09:00:00+00'::timestamptz + (n || ' minutes')::interval`
- type `[house,apartment,land,commercial][(n-1)%4]`
- level `[low,medium,high][((n-1)/4)%3]` (division entière)
- score : low → 12 si n≤12 sinon 28 ; medium → 41/58 ; high → 72/91
- lon `-15.68 + 0.032*((n-1)%6)`, lat `11.82 + 0.036*((n-1)/6)`
- `precision_level` : `gps` si n impair, `address` si pair
- `title` / `confidence_summary` en portugais, non vides
- `geom` : `ST_SetSRID(ST_MakePoint(lon, lat), 4326)`

- [ ] **Step 3: Commit**

```text
feat: add deterministic Bissau seed for dev profile
```

---

### Task 3: ApiException, PageResponse, handler étendu

**Files:**
- Create: `backend/src/main/java/com/immodata/gb/api/error/ApiException.java`
- Create: `backend/src/main/java/com/immodata/gb/api/PageResponse.java`
- Modify: `backend/src/main/java/com/immodata/gb/api/error/ApiExceptionHandler.java`
- Test: étendre / ajouter tests handler si besoin (couverture complète en Task 6 WebMvcTest)

**Interfaces:**
- Produces:
  - `ApiException(String code, HttpStatus status)` avec getters `getCode()`, `getStatus()`
  - `PageResponse<T>(int page, int size, long totalElements, int totalPages, List<T> items)` + factory `of(page, size, total, items)` calculant `totalPages = total==0 ? 0 : (int) ceil(total/(double)size)`
  - Handler : `ApiException` → `ApiResponse(false, null, code, Map.of())` + status ; `NoResourceFoundException` → 404 `NOT_FOUND` ; `HttpRequestMethodNotSupportedException` → 405 `METHOD_NOT_ALLOWED` ; conserver handlers `DataAccessException` et catch-all inchangés (corps health pour 503)

- [ ] **Step 1: Implémenter `ApiException` et `PageResponse`**

- [ ] **Step 2: Étendre `ApiExceptionHandler`** sans toucher la signature/corps des handlers health existants

- [ ] **Step 3: Commit**

```text
feat: add ApiException and PageResponse for property API
```

---

### Task 4: Enums, records métier, PropertyRequestParser (TDD)

**Files:**
- Create: `domain/property/PropertyType.java`, `ConfidenceLevel.java`
- Create: `application/property/BoundingBox.java`, `PropertyQuery.java`, `PropertyMapItem.java`
- Create: `api/property/PropertyRequestParser.java`
- Test: `api/property/PropertyRequestParserTest.java`

**Interfaces:**
- `PropertyType` / `ConfidenceLevel` : enum avec `String getSqlValue()` et `static Optional<X> fromParam(String)`
- `BoundingBox(double west, double south, double east, double north)`
- `PropertyQuery(BoundingBox bbox, PropertyType typeOrNull, ConfidenceLevel confidenceOrNull, int page, int size)`
- `PropertyMapItem(UUID id, UUID organisationId, String reference, String title, String propertyType, int confidenceScore, String confidenceLevel, String confidenceSummary, double longitude, double latitude)`
- `PropertyRequestParser.parseList(MultiValueMap<String,String> params) → PropertyQuery`
- `PropertyRequestParser.parseId(String raw) → UUID`
- Erreurs : `PROPERTY_BBOX_INVALID`, `PROPERTY_FILTER_INVALID`, `PROPERTY_PAGE_INVALID`, `PROPERTY_ID_INVALID`

Règles bbox (spec) : exactement 4 nombres finis, plages lon/lat, `w < e`, `s < n`. Type/confidence : vide ignoré ; répété ou hors enum → filtre invalid. Page défaut 0, size défaut 20 ; page &lt; 0 ou size hors 1..100 → page invalid. Params inconnus ignorés.

- [ ] **Step 1: Écrire `PropertyRequestParserTest` (cas listés dans la spec § Tests unitaires)** — compiler/échouer

```powershell
cd backend; .\mvnw.cmd -Dtest=PropertyRequestParserTest test
```

Expected: FAIL (classe absente ou méthodes absentes).

- [ ] **Step 2: Implémenter enums, records, parser**

- [ ] **Step 3: Relancer les tests**

Expected: PASS.

- [ ] **Step 4: Commit**

```text
feat: parse property map query parameters
```

---

### Task 5: Repository JdbcClient + PropertyQueryService

**Files:**
- Create: `infrastructure/persistence/property/PropertySpatialRepository.java`
- Create: `application/property/PropertyQueryService.java`

**Interfaces:**
- `PropertySpatialRepository.search(PropertyQuery query) → PageResponse<PropertyMapItem>`
- `PropertySpatialRepository.findById(UUID id) → Optional<PropertyMapItem>`
- SQL liste + count : spec § API (JOIN, `deleted_at IS NULL`, `&&`, filtres dynamiques sans concat de valeurs, `ORDER BY created_at DESC, id`, LIMIT/OFFSET). Paramètres nommés `JdbcClient`.
- `PropertyQueryService.list(PropertyQuery)` délègue ; `get(UUID)` → item ou `ApiException(PROPERTY_NOT_FOUND, NOT_FOUND)`

- [ ] **Step 1: Implémenter repository + service** (`@Repository` / `@Service`, constructeur `JdbcClient`)

- [ ] **Step 2: Commit**

```text
feat: query properties by bbox with PostGIS
```

---

### Task 6: PropertyController + WebMvcTest

**Files:**
- Create: `api/property/PropertyController.java`
- Test: `api/property/PropertyControllerTest.java`

**Interfaces:**
- `GET /api/v1/properties` : `@RequestParam MultiValueMap<String,String> params` → parser → service → `ApiResponse.success` enveloppe. `Cache-Control: no-store` comme health.
- `GET /api/v1/properties/{id}` : parseId → service.get
- Succès : `new ApiResponse<>(true, pageOrItem, null, Map.of())`

- [ ] **Step 1: Écrire `@WebMvcTest(PropertyController.class)`** avec `@Import(ApiExceptionHandler.class)`, mock `PropertyQueryService`

Couvrir : 400 codes, 404 NOT_FOUND / PROPERTY_NOT_FOUND, 405 POST, 404 route inconnue, 500 RuntimeException, 503 DataAccessException (message `HEALTH_DATABASE_UNAVAILABLE`), forme JSON enveloppe.

- [ ] **Step 2: Run fail puis implémenter le contrôleur**

```powershell
cd backend; .\mvnw.cmd -Dtest=PropertyControllerTest test
```

- [ ] **Step 3: Commit**

```text
feat: expose property map REST endpoints
```

---

### Task 7: Tests d'intégration PostGIS

**Files:**
- Create: `backend/src/test/java/com/immodata/gb/api/property/PropertySpatialIT.java` (ou package infra)
- Réutiliser le support Testcontainers du cycle 0 (profil `dev` → seed chargé)

**Interfaces:**
- Consomme seed + API réelle via `MockMvc` ou `TestRestTemplate` + `@SpringBootTest`

Couvrir tous les bullets spec § Intégration (24 biens, pages BIS-024…, size 101 → 400, sous-bbox 12, bord `e=-15.616` → 12, filtres 6/8/2, soft-delete transactionnel, GET id, CHECK confiance).

Assertion latitude ∈ (11,12) et longitude ∈ (-16,-15) pour tous les points seed.

- [ ] **Step 1: Écrire l'IT complète**

- [ ] **Step 2: Exécuter**

```powershell
cd backend; .\mvnw.cmd verify
```

Expected: tous les tests verts, y compris health cycle 0.

- [ ] **Step 3: Commit**

```text
test: cover property spatial API against PostGIS
```

---

### Task 8: Dépendances map + i18n + http client partagé

**Files:**
- Modify: `frontend/package.json`, `package-lock.json`
- Create: `frontend/messages/{pt,fr,en}/map.json` (clés exactes de la spec)
- Modify: `frontend/messages/{pt,fr,en}/common.json` (`nav.map`)
- Modify: `frontend/messages/{pt,fr,en}/errors.json` (3 codes PROPERTY_*)
- Modify: `frontend/i18n/request.ts` (fusionner `map`)
- Create: `frontend/lib/api/http.ts`
- Modify: `frontend/lib/api/health.ts` (délègue à `http.ts`, comportement inchangé)
- Modify: `frontend/lib/api/types.ts` (`PageResponse`, `PropertyMapItem`)
- Create: `frontend/lib/api/properties.ts`
- Test: étendre parité clés ; `properties.test.ts` ; health tests toujours verts

**Interfaces:**
- `readJson<T>(response): Promise<T>` — parse JSON quel que soit le statut ; throw `NETWORK` / `NOT_JSON` selon spec
- `fetchProperties(apiUrl, query, signal?) → { status, body: ApiResponse<PageResponse<PropertyMapItem>> }`
- Types alignés sur le JSON backend (camelCase Jackson défaut)

- [ ] **Step 1: Installer**

```powershell
cd frontend; npm install maplibre-gl@4.7.1 react-map-gl@7.1.9
```

Si résolution incompatible : ajuster aux dernières versions mineures documentées dans le README, toujours en littéral, import `react-map-gl/maplibre`.

- [ ] **Step 2: Messages + request.ts + types + http + properties + refactor health**

- [ ] **Step 3: Tests**

```powershell
cd frontend; npm test
```

Expected: PASS (cycle 0 + nouveaux).

- [ ] **Step 4: Commit**

```text
feat: add map i18n and properties API client
```

---

### Task 9: URL state + constantes carte

**Files:**
- Create: `frontend/lib/map/constants.ts` — `BISSAU_BBOX`, style OSM raster (URL tuiles + attribution spec)
- Create: `frontend/lib/map/url-state.ts` — `parseMapSearchParams`, `serializeMapSearchParams`
- Test: `frontend/lib/map/url-state.test.ts`

Règles : bbox absente/invalide → Bissau ; type/confidence invalides ignorés ; page invalide → 0 ; sérialisation arrondi 5 décimales, omettre filtres vides et `page=0` ; clamp lon/lat ; reset page à 0 si bbox/filtre change (helper dédié si utile).

- [ ] **Step 1: Tests puis implémentation**

- [ ] **Step 2: `npm test`** — PASS

- [ ] **Step 3: Commit**

```text
feat: parse and serialize map URL state
```

---

### Task 10: Composants UI hors WebGL

**Files:**
- Create: `ConfidenceBadge.tsx` (+ test)
- Create: `PropertyList.tsx` (+ test)
- Create: `FilterPanel.tsx`
- Create: `PropertyPopup.tsx`

**Interfaces:**
- Badge : couleur par `confidenceLevel` (low/medium/high), libellé i18n + `score/100` ; summary affiché par parent
- List : items boutons, range i18n, prev/next, empty state, `onSelect(item)`
- FilterPanel : selects type/confidence (« Todos ») → callbacks
- Popup : title, reference, type, badge, summary

- [ ] **Step 1: TDD badge + liste**

- [ ] **Step 2: Implémenter FilterPanel + Popup**

- [ ] **Step 3: `npm test`** — PASS

- [ ] **Step 4: Commit**

```text
feat: add map list filters and confidence badge
```

---

### Task 11: MapView, PropertyMap, page, layout, LocaleSwitcher

**Files:**
- Create: `components/map/PropertyMap.tsx` (client, CSS maplibre, markers, popup, moveend)
- Create: `components/map/MapView.tsx` (client, URL + query + `dynamic(PropertyMap, { ssr: false })`)
- Create: `app/[locale]/map/page.tsx` (server + `Suspense`)
- Modify: `app/[locale]/layout.tsx` — lien `common.nav.map`
- Modify: `LocaleSwitcher.tsx` — conserver query string (`useSearchParams`)
- Modify: `LocaleSwitcher.test.tsx`

Comportement carte (spec § Source de vérité / Carte) :
- init bounds depuis URL, padding 0, carte non contrôlée ensuite
- ignorer premier `moveend`
- debounce ~300 ms sur écriture bbox URL via `router.replace(..., { scroll: false })`
- Query `['properties', bbox, type, confidence, page]`, `placeholderData: keepPreviousData`, `retry: false`, `refetchOnWindowFocus: false`, passer `signal`
- sélection = item entier ; désélection fond / close / Escape
- clic liste : `easeTo` seulement si hors `getBounds()`
- page vide avec `totalElements > 0` et `page > 0` → remettre page 0
- marqueurs page courante, couleur = niveau confiance

- [ ] **Step 1: LocaleSwitcher + test query conservée**

- [ ] **Step 2: page + MapView + PropertyMap + layout link**

- [ ] **Step 3: `npm test`** — PASS

- [ ] **Step 4: Commit**

```text
feat: add MapLibre map page with synced list
```

---

### Task 12: README + vérification manuelle

**Files:**
- Modify: `README.md` — section Bloc 1 (démarrage, curl bbox, checklist contrôle manuel de la spec, ports de secours 8081/3010)

- [ ] **Step 1: Documenter**

- [ ] **Step 2: Vérification manuelle** (si stack dispo)

```powershell
docker compose up -d --wait
cd backend; .\mvnw.cmd spring-boot:run
# autre terminal
cd frontend; npm run dev
curl "http://localhost:8080/api/v1/properties?bbox=-15.70,11.80,-15.50,11.95"
```

Expected: `totalElements` 24, 20 items page 0 ; UI `/pt/map` conforme checklist spec.

- [ ] **Step 3: `.\mvnw.cmd verify` + `npm test` finaux**

- [ ] **Step 4: Commit**

```text
docs: document Bloc 1 map usage and checks
```

---

## Plan self-review

1. **Spec coverage:** schéma V2–V4, seed 24 + flyway locations, ApiException/handlers, parser, repo `&&`, controller, IT, i18n map, http/properties, url-state, UI, MapLibre ssr:false, LocaleSwitcher query, README — couverts Tasks 1–12. Notes identité : documentées dans la spec, pas d'implémentation.
2. **Placeholders:** versions map fixées ; seed règles explicites ; pas de TBD.
3. **Types:** `PropertyMapItem` / `PageResponse` / codes `message` alignés spec ↔ tasks.
4. **Divergence Q5 initiale :** la révision 2 remplace JPA/MapStruct par JdbcClient — ce plan suit la révision 2 uniquement.
