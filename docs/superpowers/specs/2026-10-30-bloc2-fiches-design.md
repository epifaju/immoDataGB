# Bloc 2 — Fiches biens (lecture) — Design

Date : 2026-10-30  
Statut : en relecture (révision 2)  
Périmètre : preuve verticale mince (approche A) — fiche lecture seule + sources, sans authentification  
Dépend de : Cycle 0 — Fondations (révision 2) et Bloc 1 — Cartographie (révision 2)

Ce bloc **remplace** deux affirmations du Bloc 1 : `GET /api/v1/properties/{id}` ne renvoie plus un `PropertyMapItem` mais un `PropertyDetail`, et l'UI appelle désormais cet endpoint.

## Décisions

- Approche **A** : page fiche dédiée + table `source` + colonnes `doc_status` / `address_text`.
- Navigation : lien dans la **popup carte** (« Voir la fiche ») vers `/[locale]/properties/[id]`. Pas de drawer, pas de navigation au simple clic marqueur.
- **Retour à la carte avec l'état de la carte** : le lien de la popup reprend les paramètres `bbox`, `type`, `confidence` et `page` de la carte ; la fiche les relit avec les helpers de `lib/map/url-state.ts` et reconstruit `/map?...`. Ces paramètres ne servent à aucun appel API. Au retour, la vue et les filtres sont restaurés ; la popup n'est pas rouverte.
- Accès données : **JdbcClient** (comme au Bloc 1). Pas de JPA / MapStruct / Bean Validation / Spring Security.
- Confiance reste figée en colonnes. Pas de moteur configurable (Bloc 3).
- Pas de CRUD, verify/reject, documents MinIO, historique, prix, surfaces, JWT.
- `GET /api/v1/properties/{id}` renvoie un **`PropertyDetail`** (surensemble de `PropertyMapItem`, structure plate). La liste spatiale `GET /api/v1/properties` reste en `PropertyMapItem`, sans `docStatus` ni `addressText`.
- `GET /api/v1/properties/{id}/sources` : liste ; 404 si le bien est absent ou soft-deleted ; 200 + `[]` si le bien existe sans sources. La liste n'est pas paginée (bornée par bien dans ce bloc) ; elle passera en `PageResponse` si les volumes grandissent.
- **Libellé de source** : colonne optionnelle `label` sur `source`. L'UI affiche le libellé s'il existe, sinon le type traduit.
- **`weight` est retiré** : ni colonne ni champ d'API dans ce bloc. Il est défini et ajouté au Bloc 3 (`ALTER TABLE ... ADD COLUMN`), avec la sémantique du moteur de confiance.
- Soft delete : `property.deleted_at` ; les sources n'ont pas de `deleted_at` (elles disparaissent avec le bien côté API).
- Une fiche, comme la liste, n'existe que pour un bien qui a une géométrie (jointure interne sur `property_geometry`). Le CRUD à venir devra créer le bien et sa géométrie ensemble.
- Cache HTTP : aucun en-tête `Cache-Control` explicite sur les routes `/api/v1/properties/**` (données de démonstration publiques ; la fraîcheur est gérée par TanStack Query). Le `no-store` du health reste propre au health. À réévaluer au bloc identité, quand les données seront protégées.
- Seed : profil `dev` uniquement (`db/seed`), un seul fichier répétable maintenu, mis à jour de façon idempotente.

## Critère de succès

1. Flyway applique `V6`–`V7` ; le seed met à jour les 24 biens et crée 48 sources.
2. `GET /api/v1/properties/00000000-0000-0000-0001-000000000001` renvoie un `PropertyDetail` avec `docStatus = none`, `addressText` non nul et `confidenceSummary` non vide. Pour `…0003`, `addressText` est `null`.
3. `GET /api/v1/properties/00000000-0000-0000-0001-000000000005/sources` renvoie 3 sources dans l'ordre `field_observation`, `agency_listing`, `institutional`.
4. Depuis `/pt/map`, popup → lien → fiche avec badges confiance et documentation, adresse si présente, liste des sources. Le lien « Retour à la carte » restaure la vue et les filtres de départ.
5. `./mvnw verify` et `npm test` passent ; les tests du Cycle 0 et du Bloc 1 restent verts (ceux qui assertent la forme de `{id}` sont mis à jour).

## Hors de ce bloc

Spring Security, JWT, JPA, MapStruct, Bean Validation, CRUD, verify/reject, documents, historique, estimation, moteur de confiance, `weight` des sources, `contributor_id`, `raw_data`, pagination des sources, Playwright (non requis), parcelle/dossier comme types d'entité séparés, filtre par organisation.

## Architecture

```text
Carte /map — PropertyPopup (contenu : PropertyPopupContent)
  → Link /[locale]/properties/{id}?bbox&type&confidence&page
  → PropertyDetailView
       → GET /api/v1/properties/{id}          → PropertyDetail
       → GET /api/v1/properties/{id}/sources  → List<SourceItem>   (lancé après le succès du détail)
  → « Retour à la carte » → /[locale]/map?bbox&type&confidence&page
```

Les paramètres `bbox`, `type`, `confidence` et `page` de la fiche sont lus uniquement pour reconstruire le lien de retour.

## Modèle de données

Les migrations `V6` et `V7` sont additives : elles ne modifient pas `V1`–`V4`.

### `V6__alter_property_detail_fields.sql`

Sur `property` :

| Colonne | Notes |
|---|---|
| `doc_status` VARCHAR(30) NOT NULL DEFAULT `'none'` | contrainte `property_doc_status_check` : `none` \| `declared` \| `partial` \| `verified` \| `litigious` |
| `address_text` TEXT NULL | |

Pas d'autre colonne (prix, surface, etc.). Pas d'index sur `doc_status` dans ce bloc.

### `V7__create_source.sql`

Table `source` :

| Colonne | Notes |
|---|---|
| `id` UUID PK | `gen_random_uuid()` |
| `property_id` UUID NOT NULL | FK `property(id)` |
| `source_type` VARCHAR(30) NOT NULL | contrainte `source_source_type_check` : `agency_listing` \| `field_observation` \| `scanned_doc` \| `institutional` \| `third_party_user` \| `partner` \| `open_layer` |
| `label` VARCHAR(200) NULL | contrainte `source_label_check` : nul ou non vide après `btrim` |
| `observed_at` DATE NULL | |
| `is_verified` BOOLEAN NOT NULL DEFAULT false | |
| `created_at` TIMESTAMPTZ NOT NULL DEFAULT `now()` | |

Index `(property_id, created_at, id)`, qui correspond à l'ordre de lecture. Pas de `weight`, `contributor_id`, ni `raw_data` (reportés).

### Seed

Le fichier `src/main/resources/db/seed/R__seed_bissau_dev.sql` du Bloc 1 est **régénéré** en un seul fichier maintenu. Il ne s'exécute qu'après `V2`–`V7` (les migrations répétables passent après les versionnées), donc les nouvelles colonnes existent déjà.

Idempotence, y compris sur une base de dev déjà seedée :

- biens : `INSERT ... ON CONFLICT (id) DO UPDATE SET doc_status = EXCLUDED.doc_status, address_text = EXCLUDED.address_text` ;
- sources : le fichier commence par `DELETE FROM source WHERE id BETWEEN '00000000-0000-0000-0003-000000000000' AND '00000000-0000-0000-0003-ffffffffffff'`, puis réinsère toutes les sources du seed. Seules les lignes de la plage UUID du seed sont touchées.

`docker compose down -v` n'est plus nécessaire quand les règles du seed changent.

Règles, exactes et déterministes (`n = 1..24`, référence `BIS-NNN`, comme au Bloc 1) :

- `doc_status` : `[none, declared, partial, verified, litigious][(n-1) % 5]`. Résultat : 5 `none`, 5 `declared`, 5 `partial`, 5 `verified`, 4 `litigious`.
- `address_text` : non nul si `n % 3 != 0` (16 biens), sinon nul (8 biens). Le texte est `{bairro}, Bissau`, avec `bairro` pris dans une liste fixe de 6 noms de bairros de Bissau, figée en tête du fichier et indexée par `(n-1) % 6`. L'en-tête précise que les adresses sont illustratives et sans lien avec les coordonnées des points.
- Nombre de sources par bien : `1 + (n % 3)` (de 1 à 3). Total : **48** sources (24 de rang 1, 16 de rang 2, 8 de rang 3).
- Pour la source de rang `m` (1 à `1 + (n % 3)`) du bien `n` :
  - UUID : `00000000-0000-0000-0003-00000000NNMM`, NN = `n` et MM = `m`, tous deux sur deux chiffres (douze caractères dans le dernier groupe) ; documentés en tête du fichier ;
  - `source_type` : `[field_observation, agency_listing, institutional][(n + m) % 3]` ;
  - `label` : `Observação de campo {n}.{m}`, `Agência Exemplo {n}.{m}` ou `Registo institucional {n}.{m}` selon le type ; **nul** pour `m = 3` (8 sources) ;
  - `observed_at` : `DATE '2026-08-01' + (n + m)` jours ; **nul** si `m = 3` et `n` est pair (4 sources) ;
  - `is_verified` : vrai pour `m = 1` si le bien est de niveau `high` **ou** si son `doc_status` vaut `verified`, faux sinon. Résultat : 11 sources vérifiées (8 biens `high`, 5 biens `verified`, dont 2 communs) ;
  - `created_at` : `created_at` du bien + `m` minutes (ordre de lecture déterministe).

Cas de référence pour les tests :

| `n` | `doc_status` | adresse | sources (types dans l'ordre) | vérifiée | niveau |
|---|---|---|---|---|---|
| 1 | `none` | oui | 2 : `institutional`, `field_observation` | non | `low` |
| 2 | `declared` | oui | 3 : `field_observation`, `agency_listing`, `institutional` (la 3ᵉ sans libellé ni date) | non | `low` |
| 3 | `partial` | **non** | 1 : `field_observation` | non | `low` |
| 4 | `verified` | oui | 2 : `institutional`, `field_observation` | rang 1 | `low` |
| 5 | `litigious` | oui | 3 : `field_observation`, `agency_listing`, `institutional` (la 3ᵉ sans libellé) | non | `medium` |
| 9 | `verified` | **non** | 1 : `agency_listing` | rang 1 | `high` |

## API

### `PropertyDetail`

Record (JSON camelCase), structure plate : `id`, `organisationId`, `reference`, `title`, `propertyType`, `confidenceScore`, `confidenceLevel`, `confidenceSummary`, `longitude`, `latitude`, `docStatus`, `addressText` (`null` si absent).

Le dépôt factorise la projection SQL commune avec `PropertyMapItem`, pour que les deux ne dérivent pas.

Exemple, HTTP 200 :

```json
{
  "success": true,
  "data": {
    "id": "00000000-0000-0000-0001-000000000003",
    "organisationId": "00000000-0000-0000-0000-00000000000a",
    "reference": "BIS-003",
    "title": "…",
    "propertyType": "land",
    "confidenceScore": 12,
    "confidenceLevel": "low",
    "confidenceSummary": "…",
    "longitude": -15.616,
    "latitude": 11.82,
    "docStatus": "partial",
    "addressText": null
  },
  "message": null,
  "meta": {}
}
```

### `SourceItem`

Record : `id`, `sourceType`, `label` (`null` si absent), `observedAt` (date ISO `yyyy-MM-dd` ou `null`), `isVerified`. Ni `weight`, ni `propertyId` (déjà dans le chemin). Un record garantit la clé JSON `isVerified` ; un getter `isVerified()` serait sérialisé `verified`.

Exemple, HTTP 200 :

```json
{
  "success": true,
  "data": [
    {
      "id": "00000000-0000-0000-0003-000000000501",
      "sourceType": "field_observation",
      "label": "Observação de campo 5.1",
      "observedAt": "2026-08-07",
      "isVerified": false
    }
  ],
  "message": null,
  "meta": {}
}
```

### Endpoints

**`GET /api/v1/properties/{id}`**  
Réponse `ApiResponse<PropertyDetail>`. Remplace l'ancien corps `PropertyMapItem` sur cette route.  
Erreurs : `PROPERTY_ID_INVALID` (400), `PROPERTY_NOT_FOUND` (404).

**`GET /api/v1/properties/{id}/sources`**  
Réponse `ApiResponse<List<SourceItem>>`, ordre `created_at ASC, id`. Même validation de l'id, mêmes erreurs. 404 si le bien est absent ou soft-deleted ; 200 + `[]` sinon.

**`GET /api/v1/properties` (liste)**  
Inchangé (`PropertyMapItem` sans les nouveaux champs).

Enveloppe `ApiResponse` et handlers d'erreur : ceux du Bloc 1, y compris le 503 `HEALTH_DATABASE_UNAVAILABLE` sur `DataAccessException`. Aucun en-tête `Cache-Control` explicite (voir « Décisions »).

Requêtes (`JdbcClient`, paramètres nommés) :

```sql
-- existence (même condition que le détail : bien actif avec géométrie)
SELECT EXISTS (
  SELECT 1 FROM property p
  JOIN property_geometry g ON g.property_id = p.id
  WHERE p.id = :id AND p.deleted_at IS NULL
)

-- sources
SELECT s.id, s.source_type, s.label, s.observed_at, s.is_verified
FROM source s
WHERE s.property_id = :id
ORDER BY s.created_at ASC, s.id
```

Le détail reprend la requête du Bloc 1 pour `{id}` (jointure `property` ↔ `property_geometry`, `deleted_at IS NULL`, `ST_X` / `ST_Y`) avec `p.doc_status` et `p.address_text` en plus.

## Packages Java

Étendre / ajouter :

```text
com.immodata.gb.domain.property.DocStatus
com.immodata.gb.domain.property.SourceType
com.immodata.gb.application.property.PropertyDetail
com.immodata.gb.application.property.SourceItem
com.immodata.gb.infrastructure.persistence.property.PropertySpatialRepository  // + findDetailById, existsActiveById
com.immodata.gb.infrastructure.persistence.property.SourceRepository           // listByPropertyId
com.immodata.gb.application.property.PropertyQueryService                      // + getDetail, listSources ; get supprimé
com.immodata.gb.api.property.PropertyController                                // get → PropertyDetail, + mapping /{id}/sources
```

`PropertyQueryService.getDetail(UUID)` renvoie `Optional<PropertyDetail>`. `listSources(UUID)` renvoie `Optional<List<SourceItem>>` : `Optional.empty()` si le bien est absent ou soft-deleted, liste (éventuellement vide) sinon. Le contrôleur lève `ApiException(PROPERTY_NOT_FOUND, 404)` sur un `Optional` vide, comme au Bloc 1. L'ancienne méthode `get`, devenue sans usage, est supprimée.

`DocStatus` et `SourceType` sont des enums portant la valeur SQL.

## Frontend

### Fichiers

- `app/[locale]/properties/[id]/page.tsx` : composant serveur qui passe `id` au composant client, enveloppé dans `Suspense` (lecture de `useSearchParams` pour le lien de retour).
- `components/property/PropertyDetailView.tsx` : composant client, deux requêtes TanStack Query.
- `components/property/DocStatusBadge.tsx`
- `components/property/SourceList.tsx`
- `components/map/PropertyPopupContent.tsx` : contenu de la popup (titre, type, badge, summary, lien), testable sans WebGL. `components/map/PropertyPopup.tsx` ne garde que l'enveloppe `Popup` de `react-map-gl`.
- `lib/api/properties.ts` : ajout de `fetchPropertyDetail` et `fetchPropertySources`
- `lib/api/types.ts` : `PropertyDetail`, `SourceItem`, `DocStatus`, `SourceType`, avec les listes de valeurs connues
- `lib/format/date.ts` : `formatIsoDate(iso, locale)`
- `messages/{pt,fr,en}/property.json` : fusionné par `i18n/request.ts`
- Réutiliser `components/map/ConfidenceBadge.tsx`, `lib/map/url-state.ts` et les libellés `map.type.*` et `map.confidence.*`.

### Lien de la popup et retour à la carte

- `MapView` passe à `PropertyPopupContent` la query string sérialisée de l'état de carte actuel (`bbox`, `type`, `confidence`, `page`, avec les règles de sérialisation du Bloc 1). Le lien est `/properties/{id}?{query}`, via le `Link` de `i18n/navigation.ts`, sous le summary.
- La fiche lit ces quatre paramètres avec `parseMapSearchParams`, qui ignore toute valeur invalide, puis les resérialise pour construire « Retour à la carte » : `/map?{query}`.
- Si aucun des quatre paramètres n'est présent (fiche ouverte directement), le lien de retour est `/map`. La fiche n'écrit jamais la bbox par défaut.
- Aucune URL libre n'est acceptée : seuls ces quatre paramètres, validés, sont repris. Le `LocaleSwitcher` conserve déjà la query string.

### UX fiche

- Titre (`h1`), référence, type (`map.type.*`).
- `addressText` seulement s'il est non nul et non vide après `trim`.
- `ConfidenceBadge` + texte `confidenceSummary` (badge dépendant de `confidenceLevel`, comme au Bloc 1).
- `DocStatusBadge` : palette distincte du rouge/orange/vert de la confiance — gris (`none`), bleu (`declared`), violet (`partial`), cyan (`verified`), rose (`litigious`). Le libellé traduit est toujours affiché ; la couleur n'est jamais le seul indicateur.
- `SourceList` : pour chaque source, le `label` s'il existe, sinon le type traduit ; le type traduit en information secondaire quand un libellé est affiché ; la date (`formatIsoDate`) si présente ; une pastille vérifiée / non vérifiée avec texte.
- Une valeur de `docStatus` ou de `sourceType` hors de la liste connue du front est affichée telle quelle (repli, sans plantage).
- Dates : `observedAt` est une date sans fuseau. `formatIsoDate` formate avec `timeZone: 'UTC'` pour ne pas décaler d'un jour dans les fuseaux négatifs.
- Lien « Retour à la carte » en haut de page (`property.backToMap`).

### Données et états

- Clés de requête `['property', id]` et `['property-sources', id]`, avec `retry: false` et `refetchOnWindowFocus: false`. Les sources ne sont demandées qu'après le succès du détail (`enabled`), pour éviter deux 404 et une double gestion d'erreur.
- Chargement : skeleton du détail ; skeleton de la section sources après le détail.
- Erreur du détail (pleine page, sans section sources) :
  - `PROPERTY_NOT_FOUND` **et** `PROPERTY_ID_INVALID` → `property.notFound` avec le lien de retour ;
  - `HEALTH_DATABASE_UNAVAILABLE` ou `INTERNAL_ERROR` → phrase `errors.*` ;
  - réseau, CORS, corps non JSON, code inconnu → `common.error`.
- Erreur des sources : message local dans la section sources (même table de correspondance), le détail reste affiché.
- Sources vides : `property.sources.empty`.
- La page répond HTTP 200 même pour un id inconnu (rendu client) ; c'est accepté.

### Clés i18n (à rédiger d'abord en portugais)

Il n'y a pas de clé `errors.PROPERTY_NOT_FOUND` ni `errors.PROPERTY_ID_INVALID` : ces deux codes s'affichent avec `property.notFound`.

| Clé | Portugais | Français | Anglais |
|---|---|---|---|
| `map.popup.viewDetails` (dans `map.json`) | Ver ficha | Voir la fiche | View details |
| `property.title` | Ficha do imóvel | Fiche du bien | Property details |
| `property.notFound` | Imóvel não encontrado. | Bien introuvable. | Property not found. |
| `property.backToMap` | Voltar ao mapa | Retour à la carte | Back to map |
| `property.reference` | Referência | Référence | Reference |
| `property.address` | Endereço | Adresse | Address |
| `property.confidence` | Confiança | Confiance | Confidence |
| `property.docStatus.title` | Documentação | Documentation | Documentation |
| `property.docStatus.none` | Sem documentação | Aucun document | No documents |
| `property.docStatus.declared` | Declarada | Déclarée | Declared |
| `property.docStatus.partial` | Parcial | Partielle | Partial |
| `property.docStatus.verified` | Verificada | Vérifiée | Verified |
| `property.docStatus.litigious` | Em litígio | Litigieuse | Disputed |
| `property.sources.title` | Fontes | Sources | Sources |
| `property.sources.empty` | Nenhuma fonte registada. | Aucune source enregistrée. | No sources recorded. |
| `property.sources.verified` | Verificada | Vérifiée | Verified |
| `property.sources.unverified` | Não verificada | Non vérifiée | Unverified |
| `property.sources.observedAt` | Observada em {date} | Observée le {date} | Observed on {date} |
| `property.sourceType.agency_listing` | Anúncio de agência | Annonce d'agence | Agency listing |
| `property.sourceType.field_observation` | Observação de campo | Observation de terrain | Field observation |
| `property.sourceType.scanned_doc` | Documento digitalizado | Document numérisé | Scanned document |
| `property.sourceType.institutional` | Fonte institucional | Source institutionnelle | Institutional source |
| `property.sourceType.third_party_user` | Utilizador terceiro | Utilisateur tiers | Third-party user |
| `property.sourceType.partner` | Parceiro | Partenaire | Partner |
| `property.sourceType.open_layer` | Camada aberta | Couche ouverte | Open layer |

Les clés `property.docStatus.*` couvrent les 5 valeurs et `property.sourceType.*` les 7 valeurs possibles, pas seulement celles du seed.

## Tests

### Backend

Unitaires : mapping des 5 valeurs de `DocStatus` et des 7 valeurs de `SourceType` depuis et vers la valeur SQL.

`@WebMvcTest` sur `PropertyController` avec `ApiExceptionHandler` importé et `PropertyQueryService` simulé :

- détail : 200 avec la forme `PropertyDetail` (`docStatus`, `addressText` nul sérialisé) ; 400 `PROPERTY_ID_INVALID` ; 404 `PROPERTY_NOT_FOUND` ; 500 `INTERNAL_ERROR` ; 503 `HEALTH_DATABASE_UNAVAILABLE` sur `DataAccessException` ;
- sources : 200 avec liste, 200 avec `[]`, 400 id invalide, 404 si le service renvoie `Optional.empty()` ;
- forme JSON de `SourceItem` : clés `id`, `sourceType`, `label`, `observedAt`, `isVerified` ; ni `weight`, ni `propertyId`, ni `verified`.

Intégration, Testcontainers PostGIS (profil `dev` hérité, donc seed chargé) :

- Flyway applique `V6`–`V7` ; les CHECK rejettent un `doc_status`, un `source_type` hors liste et un `label` vide ; la FK de `source` est effective ;
- comptages du seed : `doc_status` 5 / 5 / 5 / 5 / 4 ; 16 adresses non nulles et 8 nulles ; 48 sources ; 8 libellés nuls ; 4 dates nulles ; 11 sources vérifiées ; chaque bien `high` et chaque bien `verified` a une source vérifiée ; aucune source vérifiée ailleurs ; UUID des sources valides et dans la plage `…-0003-…` ;
- les 6 cas de référence du tableau : détail et sources, y compris l'ordre des types et les libellés/dates nuls ;
- bien sans source : un bien inséré par le test (avec géométrie), dans une transaction annulée en fin de test, renvoie 200 et `[]` ;
- soft delete : un bien soft-deleted (inséré par le test) renvoie 404 sur le détail **et** sur les sources ;
- liste inchangée : 24 biens, et les items de `GET /api/v1/properties` n'ont ni `docStatus` ni `addressText` ;
- les tests du Bloc 1 qui assertent la forme de `{id}` sont mis à jour pour `PropertyDetail`.

### Frontend

Vitest et Testing Library :

- `DocStatusBadge` : libellé et classe de couleur pour chaque statut, repli sur la valeur brute pour une valeur inconnue ;
- `SourceList` : libellé affiché si présent, type traduit sinon et en secondaire, date, pastille vérifiée / non vérifiée, repli pour un type inconnu, liste vide ;
- `formatIsoDate` : `2026-03-04` s'affiche le 4 mars, y compris avec un fuseau négatif simulé (`TZ=America/Sao_Paulo`) ;
- `PropertyPopupContent` : le lien pointe vers `/properties/{id}` avec les paramètres de la carte ;
- `PropertyDetailView` : chargement ; 404 et `PROPERTY_ID_INVALID` affichent `property.notFound` ; détail OK avec erreur locale des sources ; adresse masquée si nulle ou vide ; sources vides ;
- lien de retour : avec les paramètres, `/map?...` correspondant ; sans paramètre, `/map` ; paramètres invalides ignorés ;
- clients API (`fetchPropertyDetail`, `fetchPropertySources`) : corps 4xx avec code connu renvoyé tel quel, échec réseau et corps non JSON lèvent une erreur ;
- parité des clés, étendue à `property.json` et à `map.popup.viewDetails`.

### Contrôle manuel (README)

- popup → « Ver ficha » → fiche (`/pt/properties/…`) → « Voltar ao mapa » : même vue et mêmes filtres qu'au départ ;
- ouvrir une fiche directement par URL : le lien de retour mène à `/map` ;
- changer de langue sur la fiche : la query string est conservée ;
- id inconnu : message `Imóvel não encontrado.` avec le lien de retour ;
- `curl http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000005/sources` renvoie les 3 sources attendues.

## Critères d'acceptation

- [ ] Lien popup → fiche localisée, avec les paramètres de la carte
- [ ] Retour à la carte : vue et filtres restaurés ; `/map` si la fiche est ouverte directement
- [ ] Badges de confiance et de documentation distincts + summary
- [ ] Sources du seed listées, avec libellé ou type traduit, date et statut vérifié
- [ ] Soft delete → 404 sur le détail et sur les sources
- [ ] Liste carte inchangée fonctionnellement et dans son JSON
- [ ] Seed déterministe (48 sources, 6 cas de référence) et rejouable sans `down -v`
- [ ] Pas de JWT / CRUD / upload / historique / `weight`
- [ ] Tests du Cycle 0 et du Bloc 1 toujours verts

## Points à vérifier à la génération

- `V6` et `V7` n'altèrent pas `V1`–`V4`. Sur une base de dev existante, le seed régénéré met à jour les biens et recrée les sources sans `docker compose down -v`.
- Sérialisation Jackson : `observedAt` en `LocalDate` doit sortir en ISO-8601 (`yyyy-MM-dd`, pas en tableau ni en timestamp) ; `isVerified` doit sortir sous cette clé.
- `Link` de next-intl avec une query string dans le `href`, depuis un composant client (la carte l'est déjà).
- Comportement de `useSearchParams` sous `Suspense` dans `app/[locale]/properties/[id]/page.tsx` avec Next 14.2.
- `i18n/request.ts` fusionne désormais `common.json`, `errors.json`, `map.json` et `property.json`.

## Notes pour blocs suivants

- Bloc identité : protéger détail et sources selon le rôle ; protéger aussi `label` et `addressText` dès qu'ils peuvent contenir des noms réels (agences, personnes, adresses) ; passer les routes `properties` en `Cache-Control: no-store`.
- Bloc 3 : ajouter `weight` sur `source` (`CHECK (weight BETWEEN 0 AND 1)` ou l'échelle définie par le moteur) et recalculer `confidence_*` à partir des sources.
- Bloc 4 : documents liés ; ne pas confondre avec `source_type = scanned_doc` sans fichier.
- CRUD : JPA envisageable alors ; endpoints d'écriture, création du bien et de sa géométrie ensemble, workflow de `doc_status`.

## Changements de la révision 2

- **Erreur corrigée** : UUID des sources à 12 caractères dans le dernier groupe (`00000000-0000-0000-0003-00000000NNMM`).
- **Seed rejouable** : `ON CONFLICT ... DO UPDATE` pour les biens, suppression puis réinsertion des sources dans la plage UUID du seed. `docker compose down -v` n'est plus nécessaire.
- **Règles du seed exactes** : comptages de `doc_status`, adresses, 48 sources, libellés et dates nuls, 11 sources vérifiées, 6 cas de référence pour les tests.
- **Retour à la carte** (votre choix) : le lien de la popup reprend `bbox`, `type`, `confidence` et `page` ; `/map` si la fiche est ouverte directement.
- **`label` sur `source`** (votre choix), avec affichage libellé ou type traduit.
- **`weight` retiré** de la table et de l'API (votre choix), reporté au Bloc 3 ; `propertyId` retiré de `SourceItem`.
- **Cache HTTP** : la phrase « `no-store` inchangé » est remplacée par une décision explicite (aucun en-tête sur `properties`).
- **Service et dépôts** : `getDetail` remplace `get`, `listSources` renvoie un `Optional`, `SourceRepository` séparé, existence du bien avec la même condition que le détail.
- **Frontend** : `PropertyPopupContent` testable, `formatIsoDate` en UTC, repli pour les valeurs inconnues, `PROPERTY_NOT_FOUND` et `PROPERTY_ID_INVALID` vers `property.notFound`, erreur locale des sources, sources demandées après le détail, clés i18n complètes avec traductions pour les 5 statuts et les 7 types.
- **Tests complétés** : seed, contraintes, bien sans source, soft delete des deux routes, liste inchangée, forme JSON, retour à la carte, date sans décalage, parité de clés.
- **Vocabulaire** aligné : « Cycle 0 » au lieu de « Bloc 0 ».
