# Bloc 2 — Fiches biens (lecture) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exposer une fiche bien lecture seule (détail + sources) et y accéder depuis la popup carte, avec retour à la carte restaurant bbox/filtres/page.

**Architecture:** Migrations `V6`–`V7` additives + seed Bissau régénéré (48 sources). JdbcClient : `PropertyDetail` sur `GET /properties/{id}`, `SourceRepository` pour `GET /properties/{id}/sources`. Frontend : page `/properties/[id]`, `PropertyPopupContent` avec query carte, badges confiance + documentation.

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, JdbcClient, PostGIS, Next.js 14.2.35, next-intl, TanStack Query 5, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-30-bloc2-fiches-design.md` (révision 2).

## Global Constraints

- Pas de Spring Security, JWT, JPA, MapStruct, Bean Validation, CRUD, `weight`, documents, historique.
- JdbcClient uniquement ; aucune nouvelle dépendance Maven.
- Liste spatiale `GET /properties` : `PropertyMapItem` **sans** `docStatus` / `addressText`.
- `GET /properties/{id}` → `PropertyDetail` (remplace `PropertyMapItem`).
- Aucun `Cache-Control` explicite sur `/api/v1/properties/**` (retirer le `no-store` du Bloc 1 sur ces routes). Health conserve `no-store`.
- Soft delete : `property.deleted_at` ; détail et sources exigent bien actif **avec** géométrie.
- Seed : un seul `R__seed_bissau_dev.sql`, profil `dev`, 24 biens + 48 sources, règles exactes de la spec (dont cas `n=1` sources : `institutional`, `field_observation`).
- UUID sources : `00000000-0000-0000-0003-00000000NNMM` (12 hex dans le dernier groupe).
- i18n : clés d’abord en portugais ; nouveau `property.json` ; `map.popup.viewDetails`.
- Shell PowerShell ; commits via `git.exe commit -F` si besoin. Ne pas modifier `V1`–`V4`.

---

## File map

**Créer :** `V6__alter_property_detail_fields.sql`, `V7__create_source.sql`, `DocStatus`, `SourceType`, `PropertyDetail`, `SourceItem`, `SourceRepository`, `DocStatusBadge`, `SourceList`, `PropertyPopupContent`, `PropertyDetailView`, `app/.../properties/[id]/page.tsx`, `lib/format/date.ts`, `messages/*/property.json`, tests associés.

**Modifier :** seed `R__seed_bissau_dev.sql`, `PropertySpatialRepository`, `PropertyQueryService`, `PropertyController` (+ tests), `PropertySpatialIT`, `PropertyPopup` / `PropertyMap` / `MapView`, `lib/api/properties.ts`, `types.ts`, `i18n/request.ts`, `messages/*/map.json`, `README.md`.

---

### Task 1: Migrations V6–V7

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__alter_property_detail_fields.sql`
- Create: `backend/src/main/resources/db/migration/V7__create_source.sql`

**Interfaces:**
- Produces: colonnes `doc_status`, `address_text` ; table `source` selon spec § V6/V7 (contraintes nommées, index `(property_id, created_at, id)`, pas de `weight`).

- [ ] **Step 1: Écrire V6** — `ALTER TABLE property ADD COLUMN …` + CHECK `property_doc_status_check`.

- [ ] **Step 2: Écrire V7** — `CREATE TABLE source` + CHECKs + index.

- [ ] **Step 3: Commit**

```text
feat: add property detail and source schema migrations
```

---

### Task 2: Régénérer le seed Bissau

**Files:**
- Modify: `backend/src/main/resources/db/seed/R__seed_bissau_dev.sql`

**Interfaces:**
- Produces: 24 biens (ON CONFLICT DO UPDATE `doc_status`, `address_text` + champs existants), DELETE sources plage `…0003…`, INSERT 48 sources. Règles spec § Seed + tableau de référence (n=1 corrigé : `institutional`, `field_observation`).

- [ ] **Step 1: Régénérer le fichier** (en-tête UUID, bairros, org, properties avec nouvelles colonnes, sources).

- [ ] **Step 2: Vérifier comptages** (script local ou review) : 48 sources, 16 adresses, 11 verified, etc.

- [ ] **Step 3: Commit**

```text
feat: regenerate Bissau seed with sources and doc status
```

---

### Task 3: Enums et records métier

**Files:**
- Create: `domain/property/DocStatus.java`, `SourceType.java`
- Create: `application/property/PropertyDetail.java`, `SourceItem.java`
- Test: mapping SQL ↔ enum (5 + 7 valeurs)

**Interfaces:**
- `DocStatus` / `SourceType` : `getSqlValue()`, `fromSql(String)` (throw ou Optional selon pattern `PropertyType`).
- `PropertyDetail(…, String docStatus, String addressText)` — ou enums sérialisés via valeur SQL string (préférer **String SQL** dans le record JSON comme Bloc 1 `propertyType`, pour Jackson simple).
- `SourceItem(UUID id, String sourceType, String label, LocalDate observedAt, boolean isVerified)` — record, clé JSON `isVerified`.

- [ ] **Step 1: Tests enums puis implémentation**

- [ ] **Step 2: Commit**

```text
feat: add DocStatus SourceType and detail DTOs
```

---

### Task 4: Repositories + PropertyQueryService

**Files:**
- Modify: `PropertySpatialRepository.java` — `findDetailById`, `existsActiveById` ; factoriser projection commune liste/détail
- Create: `SourceRepository.java` — `listByPropertyId`
- Modify: `PropertyQueryService.java` — `getDetail` → `Optional<PropertyDetail>` ; `listSources` → `Optional<List<SourceItem>>` ; **supprimer** `get`

**Interfaces:**
- Existence = même jointure que détail (`deleted_at IS NULL` + geometry).
- Sources SQL : spec § API.

- [ ] **Step 1: Implémenter**

- [ ] **Step 2: Commit**

```text
feat: query property detail and sources via JdbcClient
```

---

### Task 5: Controller + WebMvcTest

**Files:**
- Modify: `PropertyController.java` — `get` → `PropertyDetail` ; ajouter `GET /{id}/sources` ; **retirer** `CacheControl.noStore()` sur list/get/sources
- Modify: `PropertyControllerTest.java` — forme détail, sources, 400/404/500/503, JSON `isVerified` sans `weight`/`propertyId`

**Interfaces:**
- Sur `Optional.empty()` → `ApiException(PROPERTY_NOT_FOUND, NOT_FOUND)`.

- [ ] **Step 1: Tests WebMvc puis controller**

```powershell
cd backend; .\mvnw.cmd "-Dtest=PropertyControllerTest" test
```

- [ ] **Step 2: Commit**

```text
feat: expose property detail and sources endpoints
```

---

### Task 6: Tests d’intégration PostGIS

**Files:**
- Modify: `PropertySpatialIT.java` (et/ou nouveau `PropertyDetailIT.java` si trop gros)

Couvrir : Flyway V6–V7 + CHECKs ; comptages seed ; 6 cas référence ; bien sans source → `[]` ; soft-delete 404×2 ; liste sans `docStatus`/`addressText` ; `{id}` en `PropertyDetail`.

- [ ] **Step 1: Écrire / adapter IT**

- [ ] **Step 2: `.\mvnw.cmd verify`** — BUILD SUCCESS

- [ ] **Step 3: Commit**

```text
test: cover property detail and sources against PostGIS
```

---

### Task 7: Client API, types, i18n, formatIsoDate

**Files:**
- Modify: `lib/api/types.ts`, `lib/api/properties.ts` (+ tests)
- Create: `lib/format/date.ts` (+ test TZ)
- Create: `messages/{pt,fr,en}/property.json` (clés exactes spec)
- Modify: `messages/*/map.json` (`popup.viewDetails`)
- Modify: `i18n/request.ts`, `messages/messages.test.ts`

- [ ] **Step 1: Implémenter + `npm test` ciblé**

- [ ] **Step 2: Commit**

```text
feat: add property detail API client and i18n
```

---

### Task 8: DocStatusBadge, SourceList, PropertyPopupContent

**Files:**
- Create: composants + tests (spec § Frontend tests)
- Modify: `PropertyPopup.tsx` / `PropertyMap.tsx` / `MapView.tsx` — passer query sérialisée ; contenu extrait

- [ ] **Step 1: TDD badges / liste / popup content**

- [ ] **Step 2: Brancher MapView → lien `/properties/{id}?{query}`**

- [ ] **Step 3: `npm test`**

- [ ] **Step 4: Commit**

```text
feat: add documentation badge sources list and popup link
```

---

### Task 9: Page fiche PropertyDetailView

**Files:**
- Create: `app/[locale]/properties/[id]/page.tsx`, `PropertyDetailView.tsx` (+ tests)
- Queries : `['property', id]` puis sources `enabled` après succès ; retour carte via `parseMapSearchParams` / `serializeMapSearchParams` ; UX erreurs spec

- [ ] **Step 1: Page + vue + tests**

- [ ] **Step 2: `npm test`** — PASS

- [ ] **Step 3: Commit**

```text
feat: add property detail page with map return link
```

---

### Task 10: README + vérification

**Files:**
- Modify: `README.md` — section Bloc 2 (parcours popup ↔ fiche, curls détail/sources)

- [ ] **Step 1: Documenter**

- [ ] **Step 2: `.\mvnw.cmd verify` + `npm test`**

- [ ] **Step 3: Commit**

```text
docs: document Bloc 2 property detail usage
```

---

## Plan self-review

1. **Spec coverage:** V6/V7, seed 48, detail+sources API, Cache-Control retiré, retour carte, label/sans weight, UI badges, i18n, tests — Tasks 1–10.
2. **n=1 sources:** plan et spec alignés sur `institutional`, `field_observation`.
3. **Placeholders:** aucun TBD.
