# Bloc 5 — Estimation par comparables — Design

Date : 2026-11-13  
Statut : en relecture  
Périmètre : preuve verticale mince (approche A) — observations comparables seedées, estimate PostGIS + calculateur IQR pur, page `/valuation`, sans historique ni JWT  
Dépend de : Cycle 0, Blocs 1–4

Ce bloc **ajoute** la table `comparable_observation`, l’endpoint `POST /api/v1/valuations/estimate`, un calculateur de fourchette (médiane + IQR sur prix/m²), et la page Next.js `/[locale]/valuation`.

Il **n’ajoute pas** de colonnes prix/surface sur `property`, ni de table `valuation_result`, ni d’estimation inline sur la fiche bien.

## Décisions

- Approche **A** : filtre spatial PostGIS (`ST_DWithin` sur geography) puis `ValuationCalculator` pur en Java.
- Migration **`V10`** (`V9` = documents).
- Devise **XOF**. IQR et médiane sur **prix au m²** ; fourchette et médiane **totales** = pps × `areaSqm` demandé (arrondi entier XOF).
- Endpoint **toujours ouvert** (pas de drapeau, pas de JWT) : calcul sans écriture métier.
- UI : page dédiée `/[locale]/valuation` + lien nav. Lat/lng saisis (défauts centre Bissau) ; pas de MapLibre obligatoire dans ce bloc.
- Soft-delete sur les observations (`deleted_at`). Pas de suppression physique.
- Accès données : **JdbcClient**. Pas de JPA / MapStruct / Bean Validation / Spring Security.
- Règles non négociables :
  - jamais de valeur ponctuelle sans fourchette basse/haute ;
  - `warning` **obligatoire** (non null) si `comparablesCount < 5` après exclusion des outliers ;
  - 0 comparable après filtre → erreur `VALUATION_NO_COMPARABLES`, pas de fourchette inventée.
- Score de confiance **d’estimation** distinct du score bien : `min(100, comparablesCount * 15)` ; niveaux via les mêmes bandes 0–33 / 34–66 / 67–100 (`ConfidenceLevel.fromScore`).
- Hors scope : historique d’estimations, prix sur `property`, JWT, Playwright, OCR, recalcul confiance bien, admin UI.

## Critère de succès

1. Flyway applique `V10` ; le seed crée **36** observations (9 par type) dans l’emprise Bissau.
2. `POST /api/v1/valuations/estimate` avec un apartment au centre Bissau, rayon 5 km, 24 mois, surface 80 → 200 + fourchette (`rangeLow` ≤ `medianTotal` ≤ `rangeHigh`), `currency=XOF`, `comparablesCount` ≥ 1.
3. En resserrant le rayon / période pour obtenir `comparablesCount < 5` (mais > 0) → `warning` non null ; jamais une seule valeur sans fourchette.
4. Aucun comparable → 404 `VALUATION_NO_COMPARABLES`.
5. Page `/pt/valuation` : formulaire + affichage fourchette + warning + liste ; nav i18n pt/fr/en.
6. `./mvnw verify` et `npm test` passent ; Blocs 0–4 restent verts.

## Hors de ce bloc

Spring Security, JWT, `valuation_result`, colonnes prix/surface sur `property`, estimation depuis la fiche, MapLibre sur la page valuation, Playwright, confiance bien, documents, Bean Validation annotations (validation manuelle / parseur comme les blocs précédents).

## Architecture

```text
POST /api/v1/valuations/estimate
        ↓
ValuationController  (parse / validate request)
        ↓
ValuationService
  1. Clock → today
  2. ValuationRepository.findCandidates(type, point, radius, since)
  3. ValuationCalculator.compute(candidates, areaSqm) → EstimateResult
        ↓
ApiResponse<EstimateResult>
```

Packages sous `com.immodata.gb` :

```text
domain.valuation.ValuationCalculator
domain.valuation.ComparableFact
domain.valuation.ValuationComputation   (interne calculateur)
application.valuation.ValuationService
application.valuation.EstimateRequest     (record API/application)
application.valuation.EstimateResult
application.valuation.ComparableItem
infrastructure.persistence.valuation.ValuationRepository
api.valuation.ValuationController
```

Réutiliser `ConfidenceLevel.fromScore`, `ApiException`, `Clock` UTC, `UuidParam` si besoin (pas d’id path ici).

## Modèle de données

### `V10__create_comparable_observation.sql`

| Colonne | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `organisation_id` | UUID NOT NULL | FK `organisation(id)` |
| `property_type` | VARCHAR(50) NOT NULL | CHECK : `house` \| `apartment` \| `land` \| `commercial` |
| `price_xof` | BIGINT NOT NULL | CHECK `> 0` |
| `area_sqm` | NUMERIC(12,2) NOT NULL | CHECK `> 0` |
| `observed_at` | DATE NOT NULL | |
| `geom` | geometry(Point, 4326) NOT NULL | |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `now()` | |
| `deleted_at` | TIMESTAMPTZ NULL | |

Index GIST sur `geom` ; index `(property_type, observed_at)` WHERE `deleted_at IS NULL`.

`price_per_sqm` n’est **pas** une colonne : `price_xof::numeric / area_sqm` en lecture.

### Seed

Étendre `R__seed_bissau_dev.sql` (ou bloc append idempotent) :

- Plage UUID : `00000000-0000-0000-0005-000000000001` … `00000000-0000-0000-0005-000000000036`.
- `DELETE FROM comparable_observation WHERE id BETWEEN …0005-000000000000 AND …0005-ffffffffffff` puis INSERT 36 lignes.
- Organisation : `00000000-0000-0000-0000-00000000000a`.
- 9 observations par `property_type`.
- Points dans le bbox Bissau du seed carte (~11.80–11.95 N, 15.70–15.50 W).
- `observed_at` : réparti sur ~24 mois avant une date de référence seed (ex. 2026-11-01).
- Prix/m² : distributions par type avec **au moins 2 outliers** volontaires pour valider l’IQR en tests.
- Documenter en tête de fichier les cas de référence pour les IT (point, type, rayon, n attendu, warning ou non).

## Calcul (spécification exacte)

Entrée calculateur : liste de `ComparableFact(id, propertyType, priceXof, areaSqm, observedAt, distanceMeters)` + `areaSqm` cible + `LocalDate today` (unused si filtre déjà SQL).

1. Si liste vide → le service lève `VALUATION_NO_COMPARABLES` (le calculateur n’est pas appelé, ou lève un résultat vide convention — **préférence : service**).
2. `pps_i = priceXof / areaSqm` (BigDecimal, scale ≥ 4 en calcul interne).
3. Si `n < 4` : **pas** d’exclusion d’outliers (échantillon trop petit pour IQR stable) ; retenus = tous.
4. Si `n ≥ 4` : Q1 = percentile 25, Q3 = percentile 75 (méthode **type R-7 / inclusive** — implémentation : index `floor((n-1)*p)` et `ceil` avec moyenne linéaire comme `ValuationCalculator` tests golden). IQR = Q3−Q1. Retenir `pps` ∈ `[Q1 - 1.5*IQR, Q3 + 1.5*IQR]`.
5. Sur les retenus (`m = comparablesCount`) :
   - `medianPps` = médiane ;
   - `rangeLowPps` = Q1 des retenus ; `rangeHighPps` = Q3 des retenus ;
   - si `m == 1` : low = high = median = ce seul pps ;
   - si `m == 2` : median = moyenne des deux ; low = min ; high = max.
6. Totaux : `round(pps * areaSqm)` vers long XOF (HALF_UP).
7. `confidenceScore = min(100, m * 15)` ; `confidenceLevel = ConfidenceLevel.fromScore(score).name().toLowerCase()` → `low`|`medium`|`high`.
8. `warning` : si `m < 5`, texte fixe PT :
   `Amostra insuficiente (n={m}). A estimativa é indicativa; recomenda-se prudência.`
   sinon `null`.
9. La réponse inclut toujours `rangeLow`, `rangeHigh`, `medianTotal` (même si égaux).

Percentiles : figer dans les tests unitaires 5 fixtures numériques (liste de pps) avec Q1/Q3/médiane/outliers attendus **caractère pour caractère** sur les totaux pour une surface donnée.

## API

### Request

Validation manuelle (comme les blocs précédents, sans Bean Validation) :

| Champ | Règle |
|---|---|
| `propertyType` | enum property |
| `areaSqm` | number > 0, ≤ 1_000_000 |
| `latitude` | −90…90 |
| `longitude` | −180…180 |
| `radiusMeters` | 100…50_000 (int) |
| `periodMonths` | 1…120 (int) |

CORS : déjà étendu POST au Bloc 4.

### Response `EstimateResult`

```json
{
  "currency": "XOF",
  "medianTotal": 12000000,
  "rangeLow": 10000000,
  "rangeHigh": 14000000,
  "medianPricePerSqm": 150000,
  "rangeLowPricePerSqm": 125000,
  "rangeHighPricePerSqm": 175000,
  "comparablesCount": 7,
  "comparablesRawCount": 9,
  "confidenceScore": 100,
  "confidenceLevel": "high",
  "warning": null,
  "comparables": [
    {
      "id": "…",
      "propertyType": "apartment",
      "priceXof": 11000000,
      "areaSqm": 80.0,
      "pricePerSqm": 137500.0,
      "observedAt": "2026-06-01",
      "distanceMeters": 420.5
    }
  ]
}
```

Jamais de champ `pointEstimate` unique sans fourchette.

### Erreurs

| Code | HTTP |
|---|---|
| `VALUATION_REQUEST_INVALID` | 400 |
| `VALUATION_NO_COMPARABLES` | 404 |
| + `HEALTH_DATABASE_UNAVAILABLE`, `INTERNAL_ERROR` | |

### SQL candidats

```sql
SELECT c.id, c.property_type, c.price_xof, c.area_sqm, c.observed_at,
       ST_Distance(c.geom::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m
FROM comparable_observation c
WHERE c.deleted_at IS NULL
  AND c.property_type = :propertyType
  AND c.observed_at >= :since
  AND ST_DWithin(
        c.geom::geography,
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
        :radiusMeters
      )
ORDER BY distance_m, c.id
```

`since = today.minusMonths(periodMonths)` via `Clock`.

## Frontend

- `app/[locale]/valuation/page.tsx` + `ValuationForm` + `ValuationResultView`.
- React Hook Form + Zod (schéma aligné bornes API).
- Défauts : `latitude=11.863`, `longitude=-15.598`, `radiusMeters=3000`, `periodMonths=24`, `propertyType=apartment`, `areaSqm=80`.
- Affichage : fourchette dominante ; médiane secondaire ; badge confiance ; alerte warning ; table comparables.
- Nav : clé `nav.valuation` dans `common.json` (pt/fr/en).
- Messages `valuation.json` + `errors.VALUATION_*`.
- TanStack Query mutation pour POST estimate.
- Vitest : formulaire valide/invalide ; résultat avec warning ; jamais d’UI « prix unique » sans low/high.

## Tests

### Unitaires

`ValuationCalculatorTest` : fixtures pps ; outliers exclus ; n=0 non applicable au calculateur ; n=1,2,3 pas d’IQR fence ; n=5 warning côté résultat ; score `min(100,m*15)` ; totaux HALF_UP.

### WebMvc

`ValuationControllerTest` : 200 forme JSON ; 400 invalid ; 404 no comparables ; fourchette toujours présente sur 200.

### Intégration

Testcontainers PostGIS : seed 36 ; estimate apartment centre → count > 0 ; rayon microscopique → NO_COMPARABLES ou warning selon seed ; soft-deleted exclu.

### Frontend

Vitest composants + parité i18n.

## Contrôle manuel (README)

```text
curl.exe -X POST "http://localhost:8080/api/v1/valuations/estimate" ^
  -H "Content-Type: application/json" ^
  -d "{\"propertyType\":\"apartment\",\"areaSqm\":80,\"latitude\":11.863,\"longitude\":-15.598,\"radiusMeters\":5000,\"periodMonths\":24}"
```

Ouvrir `/pt/valuation`. Documenter que les montants sont en XOF et que n < 5 affiche un avertissement.

## Critères d’acceptation

- [ ] Médiane + IQR sur prix/m² ; fourchette totale obligatoire
- [ ] Outliers IQR×1.5 si n ≥ 4
- [ ] Warning PT si n < 5 ; 404 si n = 0
- [ ] Seed 36 + PostGIS ST_DWithin
- [ ] Page valuation + nav i18n
- [ ] Pas d’historique, pas de JWT, pas de prix sur property
- [ ] `./mvnw verify` + `npm test` verts

## Points à vérifier à la génération

- Méthode exacte de percentile (aligner tests golden et code une seule fois).
- Cast `geometry` → `geography` pour distances en mètres.
- Expansion NUMERIC JDBC → BigDecimal.
- CORS déjà OK pour POST depuis le frontend (Bloc 4).

## Notes pour blocs suivants

- Persister `valuation_result` + audit.
- Brancher estimation depuis la fiche quand `area`/`price` existent sur property.
- Pondérer la confiance d’estimation par score/confiance des biens sources si les comparables deviennent des ventes liées.
- Carte des comparables sur `/valuation`.
