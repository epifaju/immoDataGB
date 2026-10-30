# Bloc 3 — Moteur de confiance — Design

Date : 2026-10-31  
Statut : en relecture (révision 2)  
Périmètre : preuve verticale mince (approche A) — moteur isolé + règles en base + recalcul API protégé par un drapeau, sans admin UI ni JWT  
Dépend de : Cycle 0 (révision 2), Bloc 1 (révision 2), Bloc 2 (révision 2, fiches + sources)

Ce bloc **ajoute** le recalcul configurable des colonnes `confidence_score`, `confidence_level` et `confidence_summary` déjà lues par la carte et la fiche. Il **étend** `SourceItem` avec `weight`.

Il remplace trois affirmations du Bloc 2 : « pas de `weight` » (la colonne et le champ d'API arrivent ici), « `SourceItem` n'a pas de `weight` » dans les tests, et les `INSERT` du seed des sources, qui renseignent désormais `weight`.

## Décisions

- Approche **A** : un calculateur pur (`ConfidenceCalculator`), un `ConfidenceEngineService` d'orchestration, une table `confidence_rule`, deux endpoints `POST` de recalcul (bien, organisation).
- Accès données : **JdbcClient**. Pas de JPA / MapStruct / Bean Validation / Spring Security.
- Migration **`V8`** (le numéro `V7` est pris par `source`).
- `rule_type` **fermés** + `condition_json` à schéma fixe par type, validé par des CHECK en base et par une lecture stricte côté Java. Pas de DSL libre.
- Types de règles : `source_count`, `source_type_bonus`, `verified_source_bonus`, `doc_status_bonus`, `age_penalty`, `inconsistency_penalty` (réservé).
- Recalcul **explicite via API** uniquement. Pas de hook sur écriture (pas de CRUD sources/documents), pas de `@Scheduled`, pas de recalcul à la lecture.
- **Drapeau de protection** : `app.confidence.recalculation-enabled`, faux par défaut (`application.yml`), vrai dans `application-dev.yml`. Tant qu'il est faux, les deux endpoints répondent 404 `NOT_FOUND`, avant toute validation. C'est la seule protection de ces routes : il n'y a pas de sécurité dans ce bloc (voir « Notes pour blocs suivants »).
- Colonne **`source.weight`** : `SMALLINT NOT NULL DEFAULT 1`, contrainte 1–100, exposée en lecture sur `GET …/sources`. Les **unités** d'un bien sont la somme des `weight` de ses sources. Le seed attribue le poids **par type de source** : `institutional` 3, `field_observation` 2, les autres (dont `agency_listing`) 1.
- `verified_source_bonus` utilise `is_verified` (Bloc 2), qui devient un critère de confiance. Sans cette règle, le score brut maximum du seed est 60 et le niveau `high` est inatteignable.
- `confidence_summary` généré en **portugais** (langue produit par défaut) à partir du **type** de chaque règle appliquée et de ses paramètres, jamais du `rule_name`. Pas d'i18n du texte moteur dans ce bloc ; le résultat expose `appliedRules` pour une traduction ultérieure côté UI.
- Les scores initiaux du seed (Bloc 1) sont **conservés** pour la démo et pour les tests des Blocs 1 et 2. Un recalcul les **écrase** ; seul `docker compose down -v` les rétablit.
- Horloge injectable : un bean `Clock` (UTC), remplacé par une horloge fixe dans tous les tests.
- `inconsistency_penalty` : type réservé, **aucune règle seed**, et une règle de ce type ne peut pas être active (CHECK).
- Pas de CRUD `confidence_rule`, pas d'historique de scores, pas de documents MinIO, pas d'UI de recalcul.

## Critère de succès

1. Flyway applique `V8` ; le seed crée 9 règles pour l'organisation pilote et renseigne `weight` sur les 48 sources (3 pour `institutional`, 2 pour `field_observation`, 1 pour `agency_listing`).
2. Avec `app.confidence.recalculation-enabled=true` (profil `dev`), `POST /api/v1/properties/00000000-0000-0000-0001-000000000003/confidence/recalculate` renvoie un `ConfidenceResult` avec `rawScore = 30`, `confidenceScore = 30`, `confidenceLevel = low`, les règles `source_units_1` et `doc_partial_bonus`, et persiste score, niveau et résumé.
3. `POST /api/v1/organisations/00000000-0000-0000-0000-00000000000a/confidence/recalculate` renvoie `recalculatedCount = 24` ; la répartition obtenue est 14 `medium`, 7 `low`, 3 `high` (horloge au 2026-10-31).
4. `GET …/sources` expose `weight` ; la fiche l'affiche.
5. Avec le drapeau à `false`, les deux `POST` répondent 404 `NOT_FOUND`.
6. `./mvnw verify` et `npm test` passent ; les tests des Blocs 0 à 2 restent verts (ceux qui assertent `SourceItem` sont mis à jour).

## Hors de ce bloc

Spring Security, JWT, admin UI / `ConfidenceRuleEditor`, CRUD des règles par HTTP, hooks automatiques sur écriture, `@Scheduled`, recalcul à la lecture, documents MinIO, `inconsistency_penalty` actif, historique de scores, Playwright (non requis), filtre organisation côté client, traduction du résumé moteur.

## Architecture

```text
POST /api/v1/properties/{id}/confidence/recalculate
POST /api/v1/organisations/{orgId}/confidence/recalculate
        ↓
ConfidenceController  (drapeau → 404 NOT_FOUND si désactivé ; ids → UUID)
        ↓
ConfidenceEngineService   (@Transactional, Clock)
  1. verrouille et charge le(s) bien(s) actif(s) avec géométrie (FOR UPDATE, ordre par id)
  2. charge les règles actives de l'organisation, une fois (priority ASC, id ASC), parsées et validées
  3. charge les sources de tous les biens en une requête
  4. pour chaque bien : ConfidenceCalculator.compute(entrées, règles, date du jour)
  5. UPDATE property SET confidence_*, updated_at = now()
        ↓
ConfidenceResult / ConfidenceBatchResult
```

Packages sous `com.immodata.gb` :

```text
domain.confidence.ConfidenceRuleType              // enum des 6 types
domain.confidence.ConfidenceCondition             // interface scellée + records par type
domain.confidence.ConfidenceRule                  // id, name, type, condition, scoreDelta, priority
domain.confidence.ConfidenceInput                 // docStatus, List<SourceFact>, date du jour
domain.confidence.SourceFact                      // type, weight, verified, observedAt
domain.confidence.ConfidenceCalculator            // fonction pure, sans accès base
domain.confidence.ConfidenceComputation           // rawScore, score, level, appliedRules, summary
domain.confidence.ConfidenceSummaryFormatter      // gabarits en portugais
application.confidence.ConfidenceEngineService
application.confidence.ConfidenceResult / ConfidenceBatchResult / AppliedRule
application.confidence.ConfidenceRuleInvalidException
infrastructure.persistence.confidence.ConfidenceRepository      // biens, sources, mise à jour
infrastructure.persistence.confidence.ConfidenceRuleRepository  // règles actives + lecture stricte du JSON
infrastructure.config.ClockConfig                  // bean Clock UTC
infrastructure.config.ConfidenceProperties         // app.confidence.recalculation-enabled
api.confidence.ConfidenceController
api.common.UuidParam                               // conversion texte → UUID avec code d'erreur
```

`ConfidenceLevel.fromScore` (déjà dans `domain.property`) est la seule source des bandes côté Java. Le moteur ne modifie pas les services carte et fiche, hormis la lecture de `weight` dans `SourceRepository` / `SourceItem`.

`ConfidenceProperties` est un `@ConfigurationProperties("app.confidence")` (record, `recalculationEnabled` à `false` par défaut), enregistré par `@ConfigurationPropertiesScan` sur `ImmoDataApplication`.

## Modèle de données

### `V8__create_confidence_engine.sql`

**1. Colonne `weight` sur `source`**

| Colonne | Notes |
|---|---|
| `weight` SMALLINT NOT NULL DEFAULT 1 | contrainte `source_weight_check` : `BETWEEN 1 AND 100` |

Les sources existantes reçoivent 1 ; le seed les remplace ensuite par le poids par type.

**2. Table `confidence_rule`**

| Colonne | Notes |
|---|---|
| `id` UUID PK | `gen_random_uuid()` |
| `organisation_id` UUID NOT NULL | FK `organisation(id)` |
| `rule_name` VARCHAR(100) NOT NULL | identifiant lisible stable ; `UNIQUE (organisation_id, rule_name)` |
| `rule_type` VARCHAR(50) NOT NULL | CHECK fermé (ci-dessous) |
| `condition_json` JSONB NOT NULL | CHECK : objet JSON avec les clés requises par type |
| `score_delta` SMALLINT NOT NULL | CHECK `BETWEEN -100 AND 100` |
| `is_active` BOOLEAN NOT NULL DEFAULT true | |
| `priority` SMALLINT NOT NULL DEFAULT 0 | |
| `created_at` / `updated_at` TIMESTAMPTZ NOT NULL DEFAULT `now()` | |

CHECK sur `rule_type` : `source_count` \| `source_type_bonus` \| `verified_source_bonus` \| `doc_status_bonus` \| `age_penalty` \| `inconsistency_penalty`.

CHECK `confidence_rule_condition_check` (les clés sont testées avec `jsonb_exists(condition_json, '…')` plutôt qu'avec l'opérateur `?`, pour éviter toute ambiguïté avec les paramètres JDBC) :

```sql
CHECK (
  jsonb_typeof(condition_json) = 'object' AND (
    (rule_type = 'source_count'          AND jsonb_exists(condition_json, 'minUnits') AND jsonb_exists(condition_json, 'maxUnits')) OR
    (rule_type = 'source_type_bonus'     AND jsonb_exists(condition_json, 'sourceType')) OR
    (rule_type = 'verified_source_bonus' AND jsonb_exists(condition_json, 'minVerified')) OR
    (rule_type = 'doc_status_bonus'      AND jsonb_exists(condition_json, 'docStatus')) OR
    (rule_type = 'age_penalty'           AND jsonb_exists(condition_json, 'olderThanMonths')) OR
    (rule_type = 'inconsistency_penalty')
  )
)
```

CHECK `confidence_rule_reserved_check` : `rule_type <> 'inconsistency_penalty' OR is_active = false`.

Index : `(organisation_id, is_active, priority, id)`.

### Schémas `condition_json` (fermés)

| `rule_type` | JSON | Match si |
|---|---|---|
| `source_count` | `{ "minUnits": int, "maxUnits": int \| null }` | `unités` ∈ [`minUnits`, `maxUnits`] (`null` = pas de plafond), où `unités = Σ weight` des sources du bien. Contraintes : `minUnits ≥ 0`, `maxUnits` nul ou `≥ minUnits`. |
| `source_type_bonus` | `{ "sourceType": "<valeur de source_type>" }` | au moins une source de ce type |
| `verified_source_bonus` | `{ "minVerified": int }` | au moins `minVerified` sources avec `is_verified = true` (`minVerified ≥ 1`) |
| `doc_status_bonus` | `{ "docStatus": "none"\|"declared"\|"partial"\|"verified"\|"litigious" }` | `property.doc_status` égal |
| `age_penalty` | `{ "olderThanMonths": int }` | voir ci-dessous (`olderThanMonths ≥ 1`) |
| `inconsistency_penalty` | réservé | jamais évalué : une règle de ce type ne peut pas être active |

`age_penalty` : la date de référence est `LocalDate.now(clock)` (UTC). Une source est **ancienne** si `observed_at < référence.minusMonths(N)` (comparaison stricte). La règle s'applique si le bien a **au moins une source datée** et que **toutes** les sources datées sont anciennes. Les sources sans date sont ignorées ; si toutes les dates sont nulles, la règle ne s'applique pas.

Une règle **inactive** (`is_active = false`) n'est jamais chargée.

### Lecture stricte des conditions (Java)

`ConfidenceRuleRepository` convertit `condition_json` en records typés avec un `ObjectMapper` dédié : clés inconnues refusées, valeur nulle pour un entier refusée, valeur d'énumération inconnue refusée, contraintes de bornes ci-dessus vérifiées. Toute règle **active** invalide lève `ConfidenceRuleInvalidException` : le recalcul échoue en **500 `INTERNAL_ERROR`**, le détail (identifiant et nom de la règle) est journalisé côté serveur, et aucun bien n'est modifié. Les règles sont chargées et validées avant toute mise à jour.

## Scoring

Le calculateur est une fonction pure : `compute(ConfidenceInput, List<ConfidenceRule>)`.

1. `rawScore = 0`.
2. Parcourir les règles actives dans l'ordre `(priority ASC, id ASC)` ; si une règle correspond, `rawScore += scoreDelta` et ajouter la règle à `appliedRules` (`ruleName`, `ruleType`, `scoreDelta`). L'ordre n'influence **pas** le score (c'est une somme) : il ne fixe que l'ordre des critères dans l'explication.
3. `score = clamp(rawScore, 0, 100)`.
4. `level = ConfidenceLevel.fromScore(score)` : `low` 0–33, `medium` 34–66, `high` 67–100, en accord avec le CHECK de `property`.
5. `summary` : voir ci-dessous.

Comportements précisés :

- Un bien **sans source** a 0 unité : aucune règle `source_count` ne s'applique (si `minUnits ≥ 1`), mais les règles `doc_status_bonus` peuvent encore s'appliquer. C'est assumé : la documentation seule peut contribuer au score.
- Une organisation **sans règle active** : tous ses biens passent à 0, `low`, avec le texte « Sem evidências suficientes para pontuar. » et `appliedRules` vide.
- Le recalcul d'un bien utilise les règles de **l'organisation du bien**.

### Résumé (portugais)

- Aucune règle appliquée : `Sem evidências suficientes para pontuar.`
- Sinon : `Pontuação {score}/100 ({nível}). Critérios: {critère} ({delta}); … .` — niveau : `baixa` (low), `média` (medium), `alta` (high) ; critères séparés par `; ` dans l'ordre des règles ; delta signé en ASCII (`+20`, `-30`).
- Texte d'un critère, selon le **type** de la règle :

| `rule_type` | Texte |
|---|---|
| `source_count` | `1 unidade de fonte` ou `{n} unidades de fonte` (n = unités du bien) |
| `source_type_bonus` | `tipo de fonte: {tipo}` ; `anúncio de agência`, `observação de campo`, `documento digitalizado`, `fonte institucional`, `utilizador terceiro`, `parceiro`, `camada aberta` |
| `verified_source_bonus` | `1 fonte verificada` ou `{n} fontes verificadas` (n = sources vérifiées du bien) |
| `doc_status_bonus` | `documentação {estado}` ; `inexistente` (none), `declarada`, `parcial`, `verificada`, `em litígio` |
| `age_penalty` | `fontes com mais de {N} meses` |

Exemple : `Pontuação 30/100 (baixa). Critérios: 1 unidade de fonte (+20); documentação parcial (+10).`

### Persistance

`UPDATE property SET confidence_score, confidence_level, confidence_summary, updated_at = now() WHERE id = :id AND deleted_at IS NULL`. `updated_by` reste nul. Les trois colonnes sont écrites ensemble, donc le CHECK niveau/score est toujours respecté.

## Seed

Fichier `R__seed_bissau_dev.sql` **étendu** (toujours un seul fichier répétable, profil `dev`).

### `weight` des sources

Les `INSERT` des sources incluent `weight` : `institutional` → 3, `field_observation` → 2, `agency_listing` → 1. Le `DELETE` de la plage UUID `…-0003-…` reste en tête du bloc des sources. Unités par bien : 1 (une source `agency_listing`), 5 (`institutional` + `field_observation`) ou 6 (les trois types).

### Règles de l'organisation pilote (`…0000-00000000000a`)

UUID `00000000-0000-0000-0004-0000000000NN` (NN de 01 à 09). Idempotence : le fichier supprime d'abord `DELETE FROM confidence_rule WHERE id BETWEEN '00000000-0000-0000-0004-000000000000' AND '00000000-0000-0000-0004-ffffffffffff'`, puis réinsère les 9 règles. Un `UPDATE` SQL manuel fait en dev est donc écrasé quand le fichier de seed change.

| NN | priority | rule_name | type | condition | delta |
|---|---|---|---|---|---|
| 01 | 10 | `source_units_1` | `source_count` | `{"minUnits":1,"maxUnits":1}` | +20 |
| 02 | 20 | `source_units_2_plus` | `source_count` | `{"minUnits":2,"maxUnits":null}` | +15 |
| 03 | 30 | `field_observation_bonus` | `source_type_bonus` | `{"sourceType":"field_observation"}` | +20 |
| 04 | 35 | `verified_source_bonus` | `verified_source_bonus` | `{"minVerified":1}` | +20 |
| 05 | 40 | `doc_declared_bonus` | `doc_status_bonus` | `{"docStatus":"declared"}` | +10 |
| 06 | 50 | `doc_partial_bonus` | `doc_status_bonus` | `{"docStatus":"partial"}` | +10 |
| 07 | 60 | `doc_verified_bonus` | `doc_status_bonus` | `{"docStatus":"verified"}` | +25 |
| 08 | 70 | `doc_litigious_penalty` | `doc_status_bonus` | `{"docStatus":"litigious"}` | −30 |
| 09 | 80 | `stale_sources_penalty` | `age_penalty` | `{"olderThanMonths":24}` | −5 |

Les noms `source_units_*` portent sur des **unités** (somme des poids), pas sur un nombre de sources.

Le seed **ne recalcule pas** les colonnes de confiance : les valeurs du Bloc 1 sont conservées. Pour les rétablir après un recalcul en dev, `docker compose down -v`.

### Résultats attendus après recalcul de l'organisation

Horloge fixée au 2026-10-31. Les dates de seed sont en août 2026 : `stale_sources_penalty` ne s'applique à aucun bien. Score brut maximum : 80 ; minimum : −10 (bien 15).

| `n` | unités | `doc_status` | sources vérifiées | score brut | score | niveau |
|---|---|---|---|---|---|---|
| 1 | 5 | `none` | 0 | 35 | 35 | medium |
| 2 | 6 | `declared` | 0 | 45 | 45 | medium |
| 3 | 1 | `partial` | 0 | 30 | 30 | low |
| 4 | 5 | `verified` | 1 | 80 | 80 | high |
| 5 | 6 | `litigious` | 0 | 5 | 5 | low |
| 6 | 1 | `none` | 0 | 20 | 20 | low |
| 7 | 5 | `declared` | 0 | 45 | 45 | medium |
| 8 | 6 | `partial` | 0 | 45 | 45 | medium |
| 9 | 1 | `verified` | 1 | 65 | 65 | medium |
| 10 | 5 | `litigious` | 1 | 25 | 25 | low |
| 11 | 6 | `none` | 1 | 55 | 55 | medium |
| 12 | 1 | `declared` | 1 | 50 | 50 | medium |
| 13 | 5 | `partial` | 0 | 45 | 45 | medium |
| 14 | 6 | `verified` | 1 | 80 | 80 | high |
| 15 | 1 | `litigious` | 0 | −10 | **0** (borné) | low |
| 16 | 5 | `none` | 0 | 35 | 35 | medium |
| 17 | 6 | `declared` | 0 | 45 | 45 | medium |
| 18 | 1 | `partial` | 0 | 30 | 30 | low |
| 19 | 5 | `verified` | 1 | 80 | 80 | high |
| 20 | 6 | `litigious` | 0 | 5 | 5 | low |
| 21 | 1 | `none` | 1 | 40 | 40 | medium |
| 22 | 5 | `declared` | 1 | 65 | 65 | medium |
| 23 | 6 | `partial` | 1 | 65 | 65 | medium |
| 24 | 1 | `verified` | 1 | 65 | 65 | medium |

Répartition : 14 `medium`, 7 `low` (biens 3, 5, 6, 10, 15, 18, 20), 3 `high` (biens 4, 14, 19). Règles appliquées sur les 24 biens : `source_units_2_plus` 16, `field_observation_bonus` 16, `verified_source_bonus` 11, `source_units_1` 8, `doc_declared_bonus` 5, `doc_partial_bonus` 5, `doc_verified_bonus` 5, `doc_litigious_penalty` 4, `stale_sources_penalty` 0.

Avec une horloge fixée au 2028-12-01, toutes les sources datées sont anciennes : les 24 biens reçoivent `stale_sources_penalty` (−5), le bien 3 passe à 25, et la répartition devient 12 `medium`, 9 `low`, 3 `high`.

Résumés exacts de référence (horloge au 2026-10-31) :

- Bien 1 : `Pontuação 35/100 (média). Critérios: 5 unidades de fonte (+15); tipo de fonte: observação de campo (+20).`
- Bien 2 : `Pontuação 45/100 (média). Critérios: 6 unidades de fonte (+15); tipo de fonte: observação de campo (+20); documentação declarada (+10).`
- Bien 3 : `Pontuação 30/100 (baixa). Critérios: 1 unidade de fonte (+20); documentação parcial (+10).`
- Bien 4 : `Pontuação 80/100 (alta). Critérios: 5 unidades de fonte (+15); tipo de fonte: observação de campo (+20); 1 fonte verificada (+20); documentação verificada (+25).`
- Bien 5 : `Pontuação 5/100 (baixa). Critérios: 6 unidades de fonte (+15); tipo de fonte: observação de campo (+20); documentação em litígio (-30).`
- Bien 9 : `Pontuação 65/100 (média). Critérios: 1 unidade de fonte (+20); 1 fonte verificada (+20); documentação verificada (+25).`
- Bien 15 : `Pontuação 0/100 (baixa). Critérios: 1 unidade de fonte (+20); documentação em litígio (-30).`

## API

### `ConfidenceResult`

```json
{
  "propertyId": "00000000-0000-0000-0001-000000000003",
  "rawScore": 30,
  "confidenceScore": 30,
  "confidenceLevel": "low",
  "confidenceSummary": "Pontuação 30/100 (baixa). Critérios: 1 unidade de fonte (+20); documentação parcial (+10).",
  "appliedRules": [
    { "ruleName": "source_units_1", "ruleType": "source_count", "scoreDelta": 20 },
    { "ruleName": "doc_partial_bonus", "ruleType": "doc_status_bonus", "scoreDelta": 10 }
  ]
}
```

`rawScore` est le score avant bornage ; la somme des `scoreDelta` est égale à `rawScore`. `appliedRules` est vide si aucune règle ne s'applique.

### `ConfidenceBatchResult`

```json
{
  "organisationId": "00000000-0000-0000-0000-00000000000a",
  "recalculatedCount": 24
}
```

Le batch traite tous les biens de l'organisation avec `deleted_at IS NULL` **et** une géométrie (même condition que la fiche). `recalculatedCount` est le nombre de biens traités (mis à jour), même si leur score ne change pas ; il peut donc être inférieur au nombre de biens actifs si certains n'ont pas de géométrie. Le batch s'exécute dans **une seule transaction** : tout ou rien. Les règles sont chargées une fois, les sources en une requête groupée (pas de requêtes N+1).

### Endpoints

Les deux routes vérifient d'abord le drapeau : s'il est faux, 404 `NOT_FOUND` (avant la validation de l'identifiant).

**`POST /api/v1/properties/{id}/confidence/recalculate`**  
→ `ApiResponse<ConfidenceResult>`, sans corps de requête. Erreurs : `PROPERTY_ID_INVALID` (400), `PROPERTY_NOT_FOUND` (404 : absent, soft-deleted ou sans géométrie).

**`POST /api/v1/organisations/{orgId}/confidence/recalculate`**  
→ `ApiResponse<ConfidenceBatchResult>`. Erreurs : `ORGANISATION_ID_INVALID` (400), `ORGANISATION_NOT_FOUND` (404 : absente ou soft-deleted). Une organisation sans bien actif renvoie 200 avec `recalculatedCount: 0`.

Un `GET` sur ces chemins renvoie le 405 `METHOD_NOT_ALLOWED` existant. Les appels sont idempotents pour des données identiques (seul `updated_at` change). Autres erreurs : celles du Bloc 1 (503 `HEALTH_DATABASE_UNAVAILABLE` sur `DataAccessException`, 500 `INTERNAL_ERROR`, dont règle active invalide).

Le CORS du cycle 0 n'autorise que `GET` et `OPTIONS` : aucun changement, l'UI n'appelle pas ces routes. Comme le CORS ne bloque pas l'envoi de certains `POST` simples venus d'un autre site, le drapeau reste la protection à retenir.

Requêtes (`JdbcClient`, paramètres nommés) :

```sql
-- biens à recalculer (un bien : p.id = :id ; batch : p.organisation_id = :orgId)
SELECT p.id, p.organisation_id, p.doc_status
FROM property p
JOIN property_geometry g ON g.property_id = p.id
WHERE p.deleted_at IS NULL AND p.organisation_id = :orgId
ORDER BY p.id
FOR UPDATE OF p

-- sources de tous ces biens, une seule requête
SELECT s.property_id, s.source_type, s.weight, s.is_verified, s.observed_at
FROM source s
WHERE s.property_id IN (:ids)

-- règles actives
SELECT id, rule_name, rule_type, condition_json::text, score_delta, priority
FROM confidence_rule
WHERE organisation_id = :orgId AND is_active
ORDER BY priority, id
```

### `SourceItem` (évolution)

Ajout du champ `weight` (entier JSON). Les autres champs restent : `id`, `sourceType`, `label`, `observedAt`, `isVerified`. Toujours pas de `propertyId`.

Nouveaux codes d'erreur API : `ORGANISATION_ID_INVALID`, `ORGANISATION_NOT_FOUND`. Aucune clé i18n frontend : l'UI n'appelle pas ces routes dans ce bloc.

## Frontend

- `lib/api/types.ts` : `SourceItem.weight: number` (requis) ; mocks de tests mis à jour.
- `SourceList` : affiche le poids à côté de la pastille vérifiée / non vérifiée, avec la clé `property.sources.weight`.
- Pas d'autre écran, pas de bouton de recalcul, pas d'éditeur de règles.
- Le résumé de confiance affiché sur la carte et sur la fiche reste en portugais quelle que soit la langue de l'interface (décision assumée).

Clé i18n nouvelle :

| Clé | Portugais | Français | Anglais |
|---|---|---|---|
| `property.sources.weight` | Peso: {weight} | Poids : {weight} | Weight: {weight} |

## Tests

### Backend, unitaires

`ConfidenceCalculator` (aucune base) :

- chaque type de règle : correspondance et non-correspondance ; bornes de `minUnits` et `maxUnits` incluses, `maxUnits` nul sans plafond ; `minVerified` à 1 et à 2 ;
- `age_penalty` avec horloge fixe : exactement `N` mois (2026-08-15 avec une référence au 2028-08-15) ne correspond pas, un jour plus tôt correspond ; une source récente et une ancienne ne correspondent pas ; dates nulles ignorées ; toutes dates nulles ne correspondent pas ;
- bornage bas (bien 15 : brut −10 → 0) et bornage haut (règles synthétiques dépassant 100 → 100) ;
- l'ordre de `priority` et de `id` change `appliedRules` mais pas le score ;
- aucune règle appliquée : score 0, `low`, texte fixe, `appliedRules` vide ;
- bien sans source : les règles `doc_status_bonus` s'appliquent, pas les règles `source_count` ;
- les 7 résumés de référence, caractère pour caractère, ainsi que les pluriels (`1 unidade` / `5 unidades`, `1 fonte verificada` / `2 fontes verificadas`) ;
- les 24 résultats du tableau (score et niveau), avec les entrées recalculées à partir des règles du seed.

Lecture des conditions (`ConfidenceRuleRepository` ou son lecteur) : chaque type valide ; clé inconnue, clé manquante, entier remplacé par du texte, énumération inconnue, `minUnits > maxUnits`, `olderThanMonths < 1`, `minVerified < 1` → `ConfidenceRuleInvalidException`.

### Backend, `@WebMvcTest` sur `ConfidenceController` (avec `ApiExceptionHandler`)

- drapeau vrai : 200 pour le bien et pour le batch, forme JSON complète (`rawScore`, `appliedRules` avec `ruleName`, `ruleType`, `scoreDelta`) ;
- drapeau faux : 404 `NOT_FOUND` sur les deux routes, même avec un identifiant invalide ;
- 400 `PROPERTY_ID_INVALID` et `ORGANISATION_ID_INVALID` ; 404 `PROPERTY_NOT_FOUND` et `ORGANISATION_NOT_FOUND` ;
- 405 sur `GET` ; 500 `INTERNAL_ERROR` sur `ConfidenceRuleInvalidException` ; 503 sur `DataAccessException`.

### Backend, intégration

Testcontainers PostGIS, profil `dev` hérité (drapeau à `true`, seed chargé), `Clock` fixé au 2026-10-31 par une `@TestConfiguration`, tests **`@Transactional` avec rollback** : ils modifient des lignes que les tests des Blocs 1 et 2 comptent (8 biens `high` dans le seed d'origine). Le service participe à la transaction du test.

- Flyway applique `V8` ; 9 règles seed présentes avec les UUID de la plage `…-0004-…` ; les 48 sources ont `weight` 3 pour `institutional`, 2 pour `field_observation`, 1 pour `agency_listing` ;
- contraintes : `weight` 0 et 101 refusés ; `rule_type` inconnu ; `rule_name` en double dans une organisation ; `condition_json` sans la clé requise ou non objet ; `inconsistency_penalty` active ; `score_delta` à ±101 ;
- les 7 recalculs de référence d'un bien, avec résumé exact ;
- le batch : `recalculatedCount = 24` et les 24 couples (score, niveau) du tableau relus en base ; répartition 14 / 7 / 3 ;
- horloge au 2028-12-01 : les 24 biens ont −5 (bien 3 = 25), répartition 12 / 9 / 3 ;
- configurable sans redéploiement : modifier le `score_delta` de `doc_partial_bonus` à +20 par SQL → bien 3 à 40 `medium` ; désactiver `source_units_1` → bien 3 à 10 ; modifier une `priority` change l'ordre de `appliedRules` mais pas le score ;
- portée par organisation : un bien d'une autre organisation (inséré par le test avec sa géométrie) n'est pas modifié par le batch de l'organisation pilote ; son recalcul unitaire donne 0, `low`, le texte « Sem evidências suficientes para pontuar. » et `appliedRules` vide ;
- exclusions : bien soft-deleted ou sans géométrie → 404 sur le recalcul unitaire et exclu du batch (le compteur reste 24) ; organisation inconnue ou soft-deleted → 404 ; organisation sans bien → 200 avec `recalculatedCount: 0` ;
- règle active invalide (par exemple `{"minUnits":"x","maxUnits":null}`, qui passe les CHECK) → 500 `INTERNAL_ERROR` et aucune ligne de `property` modifiée ;
- chaque score de 0 à 100, converti par `ConfidenceLevel.fromScore`, est accepté par le CHECK de `property` ;
- `GET …/sources` expose `weight` ; la forme JSON de `SourceItem` du Bloc 2 est mise à jour (présence de `weight`, toujours ni `propertyId` ni `verified`).

### Frontend

Vitest et Testing Library :

- `SourceList` affiche le poids, avec la clé traduite ;
- types et mocks mis à jour ; parité des clés étendue à `property.sources.weight`.

## Contrôle manuel (README)

Avec le profil `dev` (drapeau à `true`) :

```text
curl -X POST "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000003/confidence/recalculate"
curl -X POST "http://localhost:8080/api/v1/organisations/00000000-0000-0000-0000-00000000000a/confidence/recalculate"
curl "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000003/sources"
```

Sous PowerShell, remplacer `curl` par `curl.exe`. Rafraîchir la fiche et la carte après le recalcul : le bien 3 affiche 30/100 avec le résumé généré, et trois biens (4, 14, 19) sont `high`. Pour retrouver les scores d'origine du seed : `docker compose down -v`. Le README documente le drapeau `app.confidence.recalculation-enabled`.

## Critères d'acceptation

- [ ] Règles configurables en base sans redéploiement (seed + `UPDATE` SQL manuel), validées par CHECK et par une lecture stricte
- [ ] Recalcul par API pour un bien et pour une organisation, atomique et déterministe, avec horloge injectable
- [ ] Justification : `confidence_summary` en portugais, `appliedRules` avec deltas, `rawScore`
- [ ] Niveau `high` atteignable (règle `verified_source_bonus`) ; bandes inchangées 0–33 / 34–66 / 67–100
- [ ] `weight` en API et dans l'UI, poids du seed par type de source
- [ ] Endpoints désactivables par drapeau, 404 `NOT_FOUND` quand il est faux
- [ ] Tests de recalcul `@Transactional`, tests des Blocs 0 à 2 inchangés et verts
- [ ] Pas d'admin UI, de JWT ni de Spring Security dans ce bloc

## Points à vérifier à la génération

- `jsonb_exists(jsonb, text)` disponible avec PostgreSQL 16 (sinon, utiliser `condition_json ? 'clé'` après vérification du comportement de Flyway).
- Expansion d'une `Collection` pour `IN (:ids)` avec `JdbcClient` ; lecture de `condition_json::text` puis parsing Jackson ; `SMALLINT` lu comme entier.
- `FOR UPDATE OF p` avec la jointure sur `property_geometry`.
- `@Transactional` sur le service avec la `JdbcTransactionManager` auto-configurée, et participation à la transaction d'un test `@Transactional` avec `MockMvc`.
- `@ConfigurationPropertiesScan` et valeur par défaut `false` du drapeau, y compris dans les `@WebMvcTest` (propriété posée explicitement par les tests qui en ont besoin).
- Remplacement du bean `Clock` par une `@TestConfiguration` primaire dans les tests d'intégration.
- `ObjectMapper` strict dédié aux conditions, sans modifier celui de l'application.

## Notes pour blocs suivants

- Bloc identité : remplacer le drapeau par une autorisation par rôle sur les deux `POST` ; journaliser qui déclenche un recalcul global.
- Historique de scores : conserver les anciens scores et `appliedRules` à chaque recalcul.
- CRUD sources et documents : déclencher le recalcul du bien concerné après écriture, avec les mêmes règles.
- Quand surfaces et prix existent : activer `inconsistency_penalty` (retirer la contrainte de règle réservée).
- Traduction du résumé : générer le texte côté UI à partir de `appliedRules`, selon la langue.
- Éditeur de règles : écrire les règles par HTTP avec la même validation que les CHECK et la lecture stricte.

## Changements de la révision 2

- **Cas de référence corrigés** : le bien 3 vaut 30 `low` (une seule source `agency_listing`), pas 50 `medium`. Table complète des 24 biens, avec résumés exacts, calculée par script.
- **Règle `verified_source_bonus`** (+20) ajoutée : `high` devient atteignable (biens 4, 14, 19 après recalcul).
- **Poids par type de source** (3 / 2 / 1) au lieu du rang ; règles renommées `source_units_1` et `source_units_2_plus`.
- **Drapeau `app.confidence.recalculation-enabled`** : endpoints désactivés par défaut, 404 `NOT_FOUND`, actifs en `dev`.
- **Résultat enrichi** : `rawScore` et `appliedRules` (nom, type, delta) à la place de `appliedRuleNames`.
- **`Clock` injectable** dans tous les tests : plus de dépendance à la date du jour pour `age_penalty`, avec définition stricte de la règle d'âge.
- **Calculateur pur** séparé du service ; conditions typées, lecture stricte, règle active invalide → 500 sans mise à jour partielle.
- **Contraintes de base** : unicité de `rule_name` par organisation, CHECK des clés de `condition_json`, de `score_delta` et de `inconsistency_penalty` inactif.
- **Résumé** généré par type de règle et paramètres, plus par `rule_name` ; gabarits portugais exacts.
- **Batch** : une transaction, verrouillage des biens, règles chargées une fois, sources en une requête, compteur défini.
- **Tests** : transactionnels avec rollback, horloge fixe, portée par organisation, règle modifiée par SQL, règle invalide, tous les scores 0–100 acceptés par le CHECK.
- **Frontend** : pas de clés `errors.ORGANISATION_*` inutiles, une seule clé nouvelle (`property.sources.weight`).
- **README** : `curl` (`curl.exe` sous PowerShell), drapeau et retour à l'état initial du seed documentés.
