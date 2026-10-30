# Bloc 3 — Moteur de confiance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Moteur de confiance configurable (règles en base, calculateur pur, recalcul API bien/org protégé par drapeau) + `weight` sur les sources en lecture UI.

**Architecture:** `V8` ajoute `source.weight` et `confidence_rule`. Domaine pur (`ConfidenceCalculator` + conditions scellées + `ConfidenceSummaryFormatter`). Orchestration `ConfidenceEngineService` (JdbcClient, `Clock`, `FOR UPDATE`, batch 1 transaction). `ConfidenceController` lit `app.confidence.recalculation-enabled` (404 `NOT_FOUND` si faux). Frontend : afficher `weight` dans `SourceList` uniquement.

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, JdbcClient, Jackson (ObjectMapper dédié conditions), PostGIS/Testcontainers, Next.js 14.2, next-intl, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-31-bloc3-confiance-design.md` (révision 2). Toute table de scores, résumés exacts, CHECK SQL et schémas JSON viennent de cette spec — les recopier, ne pas inventer.

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, admin UI, CRUD règles HTTP, hooks write, `@Scheduled`, recalcul à la lecture, documents MinIO, Playwright.
- JdbcClient uniquement ; aucune nouvelle dépendance Maven (Jackson déjà présent via Spring).
- Drapeau `app.confidence.recalculation-enabled` : **false** dans `application.yml`, **true** dans `application-dev.yml`. Si faux → 404 `NOT_FOUND` **avant** validation d’id.
- Scores seed Bloc 1/2 **conservés** ; le seed n’appelle pas le moteur. Après recalcul en dev : `docker compose down -v` pour rétablir.
- `ConfidenceLevel.fromScore(int)` est la seule source Java des bandes (alignée CHECK property).
- Horloge : bean `Clock` UTC ; tests → `Clock.fixed(..., ZoneOffset.UTC)`.
- Rule active invalide → `ConfidenceRuleInvalidException` → 500 `INTERNAL_ERROR`, **aucune** mise à jour property.
- Soft-delete : `property` / `organisation` via `deleted_at` ; bien sans géométrie = introuvable (comme Bloc 2).
- Shell PowerShell ; commits via `git.exe commit -F`. Ne pas modifier `V1`–`V7`.

---

## File map

**Créer :**
- `backend/.../db/migration/V8__create_confidence_engine.sql`
- `domain/confidence/*` (`ConfidenceRuleType`, `ConfidenceCondition` + records, `ConfidenceRule`, `ConfidenceInput`, `SourceFact`, `ConfidenceCalculator`, `ConfidenceComputation`, `ConfidenceSummaryFormatter`, `AppliedRule` domain or application)
- `domain/property/ConfidenceLevel.fromScore` (méthode ajoutée)
- `application/confidence/ConfidenceEngineService`, `ConfidenceResult`, `ConfidenceBatchResult`, `AppliedRule`, `ConfidenceRuleInvalidException`
- `infrastructure/persistence/confidence/ConfidenceRepository`, `ConfidenceRuleRepository`
- `infrastructure/config/ClockConfig`, `ConfidenceProperties`
- `api/confidence/ConfidenceController`
- `api/common/UuidParam`
- tests unit / WebMvc / IT associés

**Modifier :**
- `ImmoDataApplication` (`@ConfigurationPropertiesScan`)
- `application.yml`, `application-dev.yml`
- `ApiExceptionHandler` (handler `ConfidenceRuleInvalidException`)
- seed `R__seed_bissau_dev.sql` (weight + 9 règles)
- `SourceItem`, `SourceRepository` (+ tests / IT qui assertent la forme JSON)
- frontend `types.ts`, `SourceList` (+ test), `messages/*/property.json`, `README.md`

---

### Task 1: Migration V8

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__create_confidence_engine.sql`

**Interfaces:**
- Produces: `source.weight` + CHECK ; table `confidence_rule` avec UNIQUE `(organisation_id, rule_name)`, CHECK `rule_type`, `condition_json` via `jsonb_exists`, `score_delta` −100..100, `confidence_rule_reserved_check`, index `(organisation_id, is_active, priority, id)`. SQL exact : spec § Modèle de données.

- [ ] **Step 1: Écrire V8** (ALTER weight puis CREATE TABLE + contraintes).

- [ ] **Step 2: Commit**

```text
feat: add confidence rule schema and source weight
```

---

### Task 2: Seed weight + 9 règles

**Files:**
- Modify: `backend/src/main/resources/db/seed/R__seed_bissau_dev.sql`

**Interfaces:**
- Produces: `weight` par type (`institutional` 3, `field_observation` 2, `agency_listing` 1) ; `DELETE` plage `…0004…` + INSERT 9 règles (UUID/priority/names/deltas/JSON de la spec). Ne pas recalculer `confidence_*` des biens.

- [ ] **Step 1: Étendre INSERT sources** avec colonne `weight`.

- [ ] **Step 2: Bloc règles** DELETE + INSERT NN=01..09.

- [ ] **Step 3: Commit**

```text
feat: seed confidence rules and source weights
```

---

### Task 3: Domaine pur — calculateur, conditions, résumé, Clock, flag

**Files:**
- Create: packages `domain/confidence/*` listés dans la spec § Architecture
- Modify: `ConfidenceLevel.java` — ajouter `fromScore(int score)`
- Create: `ClockConfig`, `ConfidenceProperties`
- Modify: `ImmoDataApplication` — `@ConfigurationPropertiesScan`
- Modify: `application.yml` / `application-dev.yml`
- Test: `ConfidenceCalculatorTest`, `ConfidenceSummaryFormatterTest` (ou tests dans le même fichier), `ConfidenceLevelTest` pour `fromScore`

**Interfaces:**
- `ConfidenceCalculator.compute(ConfidenceInput input, List<ConfidenceRule> rules) → ConfidenceComputation`
- `ConfidenceComputation(int rawScore, int score, ConfidenceLevel level, List<AppliedRule> appliedRules, String summary)`
- `AppliedRule(String ruleName, String ruleType, int scoreDelta)` (JSON camelCase)
- `ConfidenceInput(String docStatus, List<SourceFact> sources, LocalDate today)`
- `SourceFact(String sourceType, int weight, boolean verified, LocalDate observedAt)`
- Conditions scellées : `SourceCountCondition(int minUnits, Integer maxUnits)`, `SourceTypeBonusCondition(String sourceType)`, `VerifiedSourceBonusCondition(int minVerified)`, `DocStatusBonusCondition(String docStatus)`, `AgePenaltyCondition(int olderThanMonths)`
- Match / résumé / clamp : **verbatim** spec §§ Scoring + Résumé + tableau des 24 + 7 résumés de référence
- `ConfidenceProperties(boolean recalculationEnabled)` défaut `false`
- `ClockConfig` : `@Bean Clock systemUTC()`

- [ ] **Step 1: Tests unitaires calculateur** (types match/non-match, âge horloge fixe, clamp, ordre appliedRules, bien sans source, 24 scores, 7 résumés caractère pour caractère) — TDD.

- [ ] **Step 2: Implémenter domaine + formatter + fromScore + Clock + properties.**

- [ ] **Step 3: `.\mvnw.cmd "-Dtest=ConfidenceCalculatorTest,ConfidenceLevel*"` test** — PASS.

- [ ] **Step 4: Commit**

```text
feat: add pure confidence calculator and summary formatter
```

---

### Task 4: Repositories confiance + lecture stricte JSON

**Files:**
- Create: `ConfidenceRuleRepository`, `ConfidenceRepository`
- Test: lecteur de conditions (clés inconnues, manquantes, types invalides, bornes) → `ConfidenceRuleInvalidException`

**Interfaces:**
- `ConfidenceRuleRepository.listActiveParsed(UUID organisationId) → List<ConfidenceRule>` — SQL spec ; parse `condition_json::text` avec ObjectMapper dédié (`FAIL_ON_UNKNOWN_PROPERTIES`, pas de null pour int) ; lève `ConfidenceRuleInvalidException(ruleId, ruleName, cause)`
- `ConfidenceRepository.lockActivePropertiesForOrg(UUID orgId)` / `lockActiveProperty(UUID id)` → list/optional de `(id, organisationId, docStatus)` avec SQL `FOR UPDATE OF p` + jointure géométrie
- `ConfidenceRepository.loadSourceFacts(Collection<UUID> propertyIds) → Map<UUID, List<SourceFact>>`
- `ConfidenceRepository.updateConfidence(UUID id, int score, String level, String summary)`
- `ConfidenceRepository.organisationExistsActive(UUID id) → boolean`

- [ ] **Step 1: Tests unitaires du parseur de conditions** (sans DB si extracteur pur, sinon avec JSON fixtures).

- [ ] **Step 2: Implémenter repos.**

- [ ] **Step 3: Commit**

```text
feat: add confidence persistence and strict rule parsing
```

---

### Task 5: `SourceItem.weight` + repository lecture

**Files:**
- Modify: `SourceItem.java`, `SourceRepository.java`
- Modify: tests / IT qui désérialisent `SourceItem` (ajout `weight`)

**Interfaces:**
- `SourceItem(..., boolean isVerified, int weight)` — ordre des composants : garder cohérence Jackson ; préférer ajouter `weight` en dernier : `(id, sourceType, label, observedAt, isVerified, weight)`.
- SQL SELECT inclut `s.weight`.

- [ ] **Step 1: Adapter record + SQL + assertions JSON (`weight` présent, pas `propertyId` / `verified`).**

- [ ] **Step 2: `.\mvnw.cmd "-Dtest=PropertyControllerTest,PropertySpatialIT"` test** — PASS (IT peut être long).

- [ ] **Step 3: Commit**

```text
feat: expose source weight on sources API
```

---

### Task 6: Engine service + DTOs API

**Files:**
- Create: `ConfidenceEngineService`, `ConfidenceResult`, `ConfidenceBatchResult`, `ConfidenceRuleInvalidException`
- Test: service unitaire optionnel avec repos mockés ; sinon couverture via WebMvc + IT

**Interfaces:**
- `recalculateProperty(UUID id) → ConfidenceResult`
- `recalculateOrganisation(UUID orgId) → ConfidenceBatchResult`
- `@Transactional` ; date = `LocalDate.now(clock)` ; règles une fois ; sources groupées ; UPDATE par bien
- `ConfidenceResult(UUID propertyId, int rawScore, int confidenceScore, String confidenceLevel, String confidenceSummary, List<AppliedRule> appliedRules)`
- `ConfidenceBatchResult(UUID organisationId, int recalculatedCount)`
- Org absente / soft-deleted → caller lève `ORGANISATION_NOT_FOUND` ; bien → `PROPERTY_NOT_FOUND`

- [ ] **Step 1: Implémenter service selon architecture spec (étapes 1–5).**

- [ ] **Step 2: Commit**

```text
feat: orchestrate confidence recalculation service
```

---

### Task 7: Controller, UuidParam, WebMvcTest, handler

**Files:**
- Create: `UuidParam`, `ConfidenceController`
- Modify: `ApiExceptionHandler` — `ConfidenceRuleInvalidException` → 500 `INTERNAL_ERROR` (body `ApiResponse` data null, log détail règle)
- Test: `ConfidenceControllerTest` (`@WebMvcTest`)

**Interfaces:**
- `UuidParam.parse(String raw, String invalidCode) → UUID` ou throw `ApiException(invalidCode, BAD_REQUEST)`
- `POST /api/v1/properties/{id}/confidence/recalculate`
- `POST /api/v1/organisations/{orgId}/confidence/recalculate`
- Si `!properties.recalculationEnabled()` → `ApiException("NOT_FOUND", NOT_FOUND)` immédiatement

- [ ] **Step 1: Tests WebMvc** (drapeau on/off, 200 formes JSON, 400/404, 405 GET, 500 rule invalid, 503 DataAccessException) — TDD.

- [ ] **Step 2: Implémenter controller + UuidParam + handler.**

- [ ] **Step 3: Commit**

```text
feat: expose confidence recalculation endpoints
```

---

### Task 8: Tests d’intégration PostGIS

**Files:**
- Create: `ConfidenceEngineIT` (ou étendre package `api.confidence`)
- `@SpringBootTest` + Testcontainers ; `@TestConfiguration` `Clock` fixed `2026-10-31T00:00:00Z` ; tests `@Transactional` rollback
- Couvrir : V8 + 9 règles + weights ; CHECKs refusés ; 7 recalculs référence + résumés ; batch 24 + répartition 14/7/3 ; clock 2028-12-01 → 12/9/3 et bien 3 = 25 ; SQL update delta / désactivation règle ; autre org non touchée ; soft-delete / sans géométrie ; rule JSON invalide → 500 sans UPDATE ; `GET sources` weight ; scores 0–100 acceptés

**Interfaces:**
- Profil héritant seed + `recalculation-enabled=true` (dev). Ne pas casser les asserts Bloc 1/2 sur le seed d’origine grâce au rollback.

- [ ] **Step 1: Écrire IT** selon spec § Backend, intégration.

- [ ] **Step 2: `.\mvnw.cmd verify`** — BUILD SUCCESS.

- [ ] **Step 3: Commit**

```text
test: cover confidence engine against PostGIS
```

---

### Task 9: Frontend weight + README

**Files:**
- Modify: `frontend/lib/api/types.ts`, `SourceList.tsx` (+ test), `messages/{pt,fr,en}/property.json`, mocks tests
- Modify: `README.md` (curls, drapeau, `docker compose down -v`)

**Interfaces:**
- Clé `property.sources.weight` : pt `Peso: {weight}` / fr `Poids : {weight}` / en `Weight: {weight}`
- Affichage à côté de la pastille vérifiée

- [ ] **Step 1: Types + i18n + SourceList + test + parité.**

- [ ] **Step 2: `npm test`** — PASS.

- [ ] **Step 3: README.**

- [ ] **Step 4: Commit**

```text
feat: show source weight on property detail
```

```text
docs: document Bloc 3 confidence recalculation
```

(un ou deux commits selon préférence ; deux commits si README séparé)

---

### Task 10: Vérification finale

- [ ] **Step 1:** `.\mvnw.cmd verify` + `npm test` sur `main` / branche feature.
- [ ] **Step 2:** Contrôle manuel README (profil `dev`) si stack up.
- [ ] **Step 3:** Finishing (merge / PR / keep).

---

## Plan self-review

1. **Spec coverage:** V8, seed 9 règles + weight par type, calculateur pur, `verified_source_bonus`, drapeau 404, `rawScore`/`appliedRules`, Clock, batch atomique, FOR UPDATE, parse strict, IT transactionnels, frontend weight only, README — Tasks 1–9.
2. **Cas bien 3 = 30 low** (agency_listing seule), high = biens 4/14/19 — ancrés Task 3 + 8.
3. **Placeholders:** aucun TBD ; tableaux et résumés = spec rev 2.
4. **Types:** `ConfidenceResult` / `AppliedRule` / `SourceItem.weight` cohérents Tasks 3–9.
5. **`fromScore`:** à ajouter (absent aujourd’hui) — Task 3.
6. **Handler 500:** dédié pour ne pas renvoyer le body `HealthView` du catch-all — Task 7.
