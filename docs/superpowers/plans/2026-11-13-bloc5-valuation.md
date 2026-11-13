# Bloc 5 — Estimation par comparables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Estimation XOF par comparables : seed PostGIS, `POST /valuations/estimate` (médiane + IQR sur prix/m² → fourchette totale), warning si n < 5, page `/valuation`.

**Architecture:** `V10` crée `comparable_observation`. `ValuationRepository` filtre via `ST_DWithin`. `ValuationCalculator` pur applique IQR×1.5, médiane, fourchette, score `min(100, n*15)`. `ValuationService` + controller ouverts (pas de drapeau). Frontend page + nav.

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, JdbcClient, PostGIS, Next.js 14.2, React Hook Form + Zod, next-intl, Vitest, Testcontainers PostGIS.

**Spec:** `docs/superpowers/specs/2026-11-13-bloc5-valuation-design.md`. Recopier algorithme, SQL, formes JSON, warning PT — ne pas inventer.

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, `valuation_result`, prix/surface sur `property`, Playwright, MapLibre sur `/valuation`.
- JdbcClient uniquement ; aucune nouvelle dépendance Maven requise.
- Endpoint estimate **toujours ouvert** (pas de drapeau).
- Jamais de valeur ponctuelle sans `rangeLow` / `rangeHigh` ; warning PT obligatoire si `comparablesCount < 5` ; 0 comparable → `VALUATION_NO_COMPARABLES`.
- Réutiliser `ConfidenceLevel.fromScore`, `ApiException`, `Clock` UTC.
- Shell PowerShell ; commits via `git.exe commit -F` (UTF-8 **sans BOM**). Ne pas modifier `V1`–`V9`.

---

## File map

**Créer :**
- `backend/src/main/resources/db/migration/V10__create_comparable_observation.sql`
- `domain/valuation/ComparableFact.java`, `ValuationCalculator.java`, `ValuationComputation.java` (ou records internes)
- `application/valuation/EstimateRequest.java`, `EstimateResult.java`, `ComparableItem.java`, `ValuationService.java`, `ValuationRequestParser.java` (validation manuelle)
- `infrastructure/persistence/valuation/ValuationRepository.java`
- `api/valuation/ValuationController.java`
- tests unit / WebMvc / IT
- `frontend/app/[locale]/valuation/page.tsx`
- `frontend/components/valuation/ValuationForm.tsx`, `ValuationResultView.tsx` (+ tests)
- `frontend/lib/api/valuation.ts`
- `frontend/messages/{pt,fr,en}/valuation.json`

**Modifier :**
- `backend/src/main/resources/db/seed/R__seed_bissau_dev.sql` (bloc 36 observations)
- `frontend/lib/api/types.ts`
- `frontend/messages/{pt,fr,en}/common.json` (`nav.valuation`)
- `frontend/messages/{pt,fr,en}/errors.json`
- `frontend/i18n/request.ts` (charger `valuation.json`)
- `frontend/app/[locale]/layout.tsx` (lien nav)
- `frontend/messages/messages.test.ts` (parité)
- `README.md`

---

### Task 1: Migration V10

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__create_comparable_observation.sql`

**Interfaces:**
- Table exacte spec § Modèle : CHECKs type/prix/surface, `geom` Point 4326, GIST, index `(property_type, observed_at)` WHERE `deleted_at IS NULL`.

- [ ] **Step 1: Écrire V10** selon spec.

- [ ] **Step 2: Commit**

```text
feat: add comparable observation schema
```

---

### Task 2: Seed 36 observations

**Files:**
- Modify: `backend/src/main/resources/db/seed/R__seed_bissau_dev.sql`

**Interfaces:**
- DELETE plage `…0005…` + INSERT 36 (9×4 types), org `…000a`, bbox Bissau, dates ~24 mois, ≥2 outliers pps, en-tête documentant 1–2 cas IT de référence.

- [ ] **Step 1: Ajouter le bloc seed idempotent.**

- [ ] **Step 2: Commit**

```text
feat: seed Bissau comparable observations
```

---

### Task 3: `ValuationCalculator` pur (TDD)

**Files:**
- Create: `ComparableFact`, `ValuationCalculator`, résultat domaine (`ValuationComputation` → mappé plus tard vers `EstimateResult`)
- Test: `ValuationCalculatorTest`

**Interfaces:**
```java
public record ComparableFact(
    UUID id, String propertyType, long priceXof, BigDecimal areaSqm,
    LocalDate observedAt, double distanceMeters) {}

// compute(List<ComparableFact> candidates, BigDecimal targetAreaSqm) → ValuationComputation
// ValuationComputation fields align EstimateResult numerics + warning + retained facts
```
- Algorithme **verbatim** spec § Calcul (n&lt;4 pas d’outliers ; n≥4 IQR×1.5 ; médiane ; Q1/Q3 retenus ; totaux HALF_UP ; score `min(100,m*15)` ; warning PT exact).
- Fixtures golden : au moins 5 jeux pps documentés dans le test.

- [ ] **Step 1: Tests unitaires** (RED) — outliers, n=1/2/3, warning n&lt;5, score, totaux.

- [ ] **Step 2: Implémenter calculateur.**

- [ ] **Step 3: `.\mvnw.cmd "-Dtest=ValuationCalculatorTest"`** — PASS.

- [ ] **Step 4: Commit**

```text
feat: add pure valuation calculator with IQR fences
```

---

### Task 4: Repository + DTOs + service

**Files:**
- Create: `EstimateRequest`, `EstimateResult`, `ComparableItem`, `ValuationRequestParser`, `ValuationRepository`, `ValuationService`
- Test: `ValuationRequestParserTest`, `ValuationServiceTest` (mocks repo + calculator + Clock)

**Interfaces:**
```java
EstimateRequest(String propertyType, BigDecimal areaSqm, double latitude, double longitude, int radiusMeters, int periodMonths)
EstimateResult(/* fields spec JSON camelCase */)
List<ComparableFact> ValuationRepository.findCandidates(String propertyType, double lat, double lng, int radiusMeters, LocalDate since)
EstimateResult ValuationService.estimate(EstimateRequest req)
```
- Parser : bornes spec → `VALUATION_REQUEST_INVALID`.
- Service : `since = LocalDate.now(clock).minusMonths(periodMonths)` ; candidats vides → `VALUATION_NO_COMPARABLES` ; sinon calculator.
- SQL candidats : spec § SQL (ST_DWithin geography).

- [ ] **Step 1: Tests parser + service (TDD).**

- [ ] **Step 2: Implémenter repo + service + DTOs.**

- [ ] **Step 3: Commit**

```text
feat: orchestrate valuation estimate from PostGIS candidates
```

---

### Task 5: Controller + WebMvcTest

**Files:**
- Create: `ValuationController`
- Test: `ValuationControllerTest`

**Interfaces:**
- `POST /api/v1/valuations/estimate` body JSON → 200 `ApiResponse<EstimateResult>`
- 400 / 404 codes spec ; JSON toujours avec `rangeLow`/`rangeHigh`/`medianTotal`/`currency` sur 200
- Pas de drapeau

- [ ] **Step 1: Tests WebMvc (TDD).**

- [ ] **Step 2: Implémenter controller.**

- [ ] **Step 3: Commit**

```text
feat: expose valuation estimate endpoint
```

---

### Task 6: IT PostGIS

**Files:**
- Create: `ValuationIT`

**Interfaces:**
- Profil `dev` + seed ; Clock fixe si utile
- Estimate apartment centre Bissau rayon 5 km → 200, count ≥ 1, fourchette ordonnée
- Rayon/période extrêmes → `VALUATION_NO_COMPARABLES` ou warning selon cas seed documenté
- Observation soft-deleted exclue
- `.\mvnw.cmd verify` — BUILD SUCCESS

- [ ] **Step 1: Écrire IT.**

- [ ] **Step 2: `.\mvnw.cmd verify`** — PASS.

- [ ] **Step 3: Commit**

```text
test: cover valuation estimate against PostGIS
```

---

### Task 7: Frontend page `/valuation`

**Files:**
- Create: page, `ValuationForm`, `ValuationResultView`, `lib/api/valuation.ts`, `messages/*/valuation.json`
- Modify: `types.ts`, `common.json` (nav), `errors.json`, `i18n/request.ts`, `layout.tsx`, `messages.test.ts`
- Test: Vitest form + result (warning, fourchette)

**Interfaces:**
- Défauts spec (lat 11.863, lng -15.598, radius 3000, period 24, apartment, area 80)
- Mutation TanStack Query → POST
- Affichage fourchette dominante ; jamais prix unique seul
- `nav.valuation` : pt `Avaliação` / fr `Estimation` / en `Valuation`

- [ ] **Step 1: i18n + API client + composants + page + nav.**

- [ ] **Step 2: `npm test`** — PASS.

- [ ] **Step 3: Commit**

```text
feat: add valuation page with estimate form
```

---

### Task 8: README + vérification finale

**Files:**
- Modify: `README.md` (curl estimate, page `/valuation`, XOF, warning n&lt;5)

- [ ] **Step 1: README.**

- [ ] **Step 2: `.\mvnw.cmd verify` + `npm test`** — PASS.

- [ ] **Step 3: Commit**

```text
docs: document Bloc 5 comparable valuation
```

- [ ] **Step 4: Finishing (merge / PR / keep) si demandé.**

---

## Plan self-review

1. **Spec coverage:** V10, seed 36, calculator IQR, ST_DWithin, endpoint ouvert, warning PT, page + nav, README, IT — Tasks 1–8. Pas d’historique ni prix property.
2. **Placeholders:** aucun TBD ; warning et bornes fichaient depuis la spec.
3. **Types:** `EstimateRequest` / `EstimateResult` / `ComparableFact` cohérents Tasks 3–7.
4. **Percentile:** Task 3 fige la méthode via tests golden ; repo ne recalcule pas.
