# Bloc 3 — Moteur de confiance — Design

Date : 2026-10-31  
Statut : en relecture  
Périmètre : preuve verticale mince (approche A) — moteur isolé + règles en base + recalcul API, sans admin UI ni JWT  
Dépend de : Cycle 0, Bloc 1, Bloc 2 (fiches + sources)

Ce bloc **ajoute** le recalcul configurable des colonnes `confidence_score`, `confidence_level` et `confidence_summary` déjà lues par la carte et la fiche. Il **étend** `SourceItem` avec `weight`.

## Décisions

- Approche **A** : `ConfidenceEngineService` isolé, table `confidence_rule`, endpoints `POST` de recalcul (bien + organisation).
- Accès données : **JdbcClient**. Pas de JPA / MapStruct / Bean Validation / Spring Security.
- Migration **`V8`** (le numéro `V7` du PRD est déjà pris par `source`).
- `rule_type` **fermés** + `condition_json` à schéma fixe par type. Pas de DSL libre.
- Recalcul **explicite via API** uniquement. Pas de hook sur writes (pas de CRUD sources/documents), pas de job `@Scheduled`, pas de recalcul à la lecture.
- Colonne **`source.weight`** : `SMALLINT NOT NULL DEFAULT 1`, CHECK 1–100, exposée en lecture sur `GET …/sources`, utilisée par `source_count` (somme des poids = unités).
- Frontend : affichage du `weight` dans `SourceList` seulement. Pas de bouton recalcul, pas d’éditeur de règles.
- `confidence_summary` généré en **portugais** (langue produit par défaut). Pas d’i18n du texte moteur dans ce bloc.
- Scores initiaux du seed Bloc 2 **conservés** pour la démo carte ; un `POST …/recalculate` les **écrase** selon les règles.
- `inconsistency_penalty` : type réservé dans le CHECK SQL, **aucune règle seed active** (pas de surface/prix).
- Pas de CRUD `confidence_rule`, pas d’historique de scores, pas de documents MinIO.

## Critère de succès

1. Flyway applique `V8` ; le seed crée les règles de l’org pilote et renseigne `weight` sur les 48 sources.
2. `POST /api/v1/properties/00000000-0000-0000-0001-000000000003/confidence/recalculate` renvoie un `ConfidenceResult` cohérent avec les règles (voir cas de référence) et persiste score/level/summary.
3. `POST /api/v1/organisations/00000000-0000-0000-0000-00000000000a/confidence/recalculate` recalcule les 24 biens actifs de l’org (`recalculatedCount = 24`).
4. `GET …/sources` expose `weight` ; l’UI fiche l’affiche.
5. `./mvnw verify` et `npm test` passent.

## Hors de ce bloc

Spring Security, JWT, admin UI / `ConfidenceRuleEditor`, CRUD règles HTTP, hooks auto sur writes, `@Scheduled`, recalcul à la lecture, documents MinIO, `inconsistency_penalty` actif, historique de scores, Playwright, filtre organisation côté client.

## Architecture

```text
POST /api/v1/properties/{id}/confidence/recalculate
POST /api/v1/organisations/{orgId}/confidence/recalculate
        ↓
ConfidenceController
        ↓
ConfidenceEngineService
  → charge bien actif (+ géométrie, comme Bloc 2) + sources + doc_status
  → charge confidence_rule actives de l’organisation (priority ASC, id ASC)
  → pour chaque règle : si match → appliquer score_delta, collecter rule_name
  → clamp [0, 100] → level (0–33 low, 34–66 medium, 67–100 high)
  → confidence_summary (pt) à partir des règles appliquées
  → UPDATE property SET confidence_*, updated_at
        ↓
ConfidenceResult / ConfidenceBatchResult
```

Packages sous `com.immodata.gb` : `domain.confidence`, `application.confidence`, `infrastructure.persistence.confidence`, `api.confidence`. Le moteur ne modifie pas les services carte/fiche au-delà de la lecture de `weight` dans `SourceRepository` / `SourceItem`.

## Modèle de données

### `V8__create_confidence_engine.sql`

**1. `ALTER TABLE source ADD COLUMN weight`**

| Colonne | Notes |
|---|---|
| `weight` SMALLINT NOT NULL DEFAULT 1 | contrainte `source_weight_check` : `BETWEEN 1 AND 100` |

**2. Table `confidence_rule`**

| Colonne | Notes |
|---|---|
| `id` UUID PK | `gen_random_uuid()` |
| `organisation_id` UUID NOT NULL | FK `organisation(id)` |
| `rule_name` VARCHAR(100) NOT NULL | identifiant lisible stable |
| `rule_type` VARCHAR(50) NOT NULL | CHECK fermé (ci-dessous) |
| `condition_json` JSONB NOT NULL | |
| `score_delta` SMALLINT NOT NULL | |
| `is_active` BOOLEAN NOT NULL DEFAULT true | |
| `priority` SMALLINT NOT NULL DEFAULT 0 | |
| `created_at` TIMESTAMPTZ NOT NULL DEFAULT `now()` | |
| `updated_at` TIMESTAMPTZ NOT NULL DEFAULT `now()` | |

`rule_type` CHECK :  
`source_count` \| `source_type_bonus` \| `doc_status_bonus` \| `age_penalty` \| `inconsistency_penalty`

Index : `(organisation_id, is_active, priority, id)`.

### Schémas `condition_json` (fermés)

| `rule_type` | JSON | Match si |
|---|---|---|
| `source_count` | `{ "minUnits": int, "maxUnits": int \| null }` | `sum(weight)` ∈ [minUnits, maxUnits] (`null` = pas de plafond) |
| `source_type_bonus` | `{ "sourceType": "<enum source>" }` | au moins une source avec ce `source_type` |
| `doc_status_bonus` | `{ "docStatus": "none"\|"declared"\|"partial"\|"verified"\|"litigious" }` | `property.doc_status` égal |
| `age_penalty` | `{ "olderThanMonths": int }` | au moins une source avec `observed_at` non nul **et** toutes les `observed_at` non nulles sont strictement plus vieilles que N mois (référence : date UTC du recalcul). Si toutes les dates sont nulles → pas de match |
| `inconsistency_penalty` | réservé | non implanté dans le moteur de ce bloc (match toujours faux si une ligne active existait) |

Règle **inactive** (`is_active = false`) : ignorée.

### Scoring

1. `score = 0`
2. Parcourir les règles actives dans l’ordre `(priority ASC, id ASC)` ; si match, `score += score_delta` et ajouter `rule_name` à `appliedRuleNames`
3. `score = clamp(score, 0, 100)`
4. `confidence_level` : `low` / `medium` / `high` selon les bandes déjà contraintes sur `property`
5. `confidence_summary` : une ou plusieurs phrases en portugais, déterministes, dérivées de `appliedRuleNames` (templates fixes dans le service). Si aucune règle ne matche : texte fixe du type « Sem evidências suficientes para pontuar. »

Persistance : `UPDATE property SET confidence_score, confidence_level, confidence_summary, updated_at = now()` pour le bien recalculé. Soft-deleted exclus.

## Seed

Fichier `R__seed_bissau_dev.sql` **étendu** (toujours un seul fichier répétable).

### `weight`

Pour la source de rang `m` : `weight = m` (donc 1, 2 ou 3). Les `INSERT` sources incluent `weight` ; le `DELETE` de plage UUID existant reste.

### Règles (org pilote `…000a`)

UUIDs déterministes `00000000-0000-0000-0004-0000000000NN`. Idempotence : `DELETE FROM confidence_rule WHERE id BETWEEN '…0004-000000000000' AND '…0004-ffffffffffff'` puis réinsertion, **ou** `ON CONFLICT (id) DO UPDATE`.

| priority | rule_name | type | condition | delta |
|---|---|---|---|---|
| 10 | `single_source_units` | `source_count` | `minUnits=1`, `maxUnits=1` | +20 |
| 20 | `multi_source_units` | `source_count` | `minUnits=2`, `maxUnits=null` | +15 |
| 30 | `field_observation_bonus` | `source_type_bonus` | `field_observation` | +20 |
| 40 | `doc_declared_bonus` | `doc_status_bonus` | `declared` | +10 |
| 50 | `doc_partial_bonus` | `doc_status_bonus` | `partial` | +10 |
| 60 | `doc_verified_bonus` | `doc_status_bonus` | `verified` | +25 |
| 70 | `doc_litigious_penalty` | `doc_status_bonus` | `litigious` | −30 |
| 80 | `stale_sources_penalty` | `age_penalty` | `olderThanMonths=24` | −5 |

Les colonnes confiance des 24 biens **ne sont pas recalculées par le seed** (valeurs Bloc 2 conservées).

### Cas de référence moteur (après recalcul, hors pénalité d’âge si dates seed 2026)

| Bien | Unités (Σ weight) | Types notables | doc_status | Score attendu (ordre) | Level |
|---|---|---|---|---|---|
| `…0003` | 1 | `field_observation` | `partial` | 20+20+10 = **50** | medium |
| `…0009` | 1 | `agency_listing` | `verified` | 20+25 = **45** | medium |
| `…0005` | 1+2+3=6 | dont `field_observation` | `litigious` | 15+20−30 = **5** | low |

(Les dates seed sont en 2026 → `stale_sources_penalty` ne matche pas si « aujourd’hui » est fin 2026 / 2026-10 ; si le calendrier de test dépasse 24 mois après `observed_at`, figer la « date de recalcul » injectable dans les tests unitaires.)

## API

### `ConfidenceResult`

```json
{
  "propertyId": "00000000-0000-0000-0001-000000000003",
  "confidenceScore": 50,
  "confidenceLevel": "medium",
  "confidenceSummary": "…",
  "appliedRuleNames": ["single_source_units", "field_observation_bonus", "doc_partial_bonus"]
}
```

### `ConfidenceBatchResult`

```json
{
  "organisationId": "00000000-0000-0000-0000-00000000000a",
  "recalculatedCount": 24
}
```

Le batch traite tous les biens de l’org avec `deleted_at IS NULL` **et** une géométrie (même jointure que la fiche). Compteur = nombre de biens effectivement mis à jour.

### Endpoints

**`POST /api/v1/properties/{id}/confidence/recalculate`**  
→ `ApiResponse<ConfidenceResult>`.  
Erreurs : `PROPERTY_ID_INVALID` (400), `PROPERTY_NOT_FOUND` (404).

**`POST /api/v1/organisations/{orgId}/confidence/recalculate`**  
→ `ApiResponse<ConfidenceBatchResult>`.  
Erreurs : `ORGANISATION_ID_INVALID` (400), `ORGANISATION_NOT_FOUND` (404). Org sans bien actif → 200 avec `recalculatedCount: 0`.

### `SourceItem` (évolution)

Ajout du champ `weight` (number / int). Les autres champs inchangés.

Messages d’erreur frontend : ajouter `ORGANISATION_ID_INVALID`, `ORGANISATION_NOT_FOUND` dans `messages/*/errors.json` (parité). Les codes propriété déjà gérés côté API ; pas d’UI de recalcul donc usage surtout pour cohérence future.

## Frontend

- Types + `fetchPropertySources` : `weight` requis.
- `SourceList` : ligne ou pastille `property.sources.weight` (ex. pt « Peso: {weight} »).
- Parité `property.json` / `errors.json`.
- Pas d’autre écran.

## Tests

### Backend

- **Unit** `ConfidenceEngineService` : fixtures (match / non-match / inactive / clamp haut et bas / âge avec horloge fixe / cas `…0003`, `…0009`, `…0005`).
- **WebMvc** `ConfidenceControllerTest` : 200 détail + batch ; 400/404 ids.
- **IT** : V8 appliquée ; règles seed présentes ; POST bien `…0003` → score 50 ; batch org → 24 ; GET sources inclut `weight` ; bien soft-delete → 404 sur recalcul bien.

### Frontend

- `SourceList` : affiche le poids.
- Parité des clés i18n.

## Contrôle manuel (README)

```text
curl.exe -X POST "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000003/confidence/recalculate"
curl.exe -X POST "http://localhost:8080/api/v1/organisations/00000000-0000-0000-0000-00000000000a/confidence/recalculate"
curl.exe "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000003/sources"
```

Vérifier score/summary sur la fiche après recalcul (rafraîchir la page).

## Critères d'acceptation

- [ ] Règles configurables en base sans redéploiement (seed + UPDATE SQL manuel possible)
- [ ] Recalcul API bien + org
- [ ] Justification textuelle (`confidence_summary` + `appliedRuleNames`)
- [ ] Badges inchangés (bandes 0–33 / 34–66 / 67–100)
- [ ] `weight` en API et UI
- [ ] Pas d’admin UI / JWT dans ce bloc
