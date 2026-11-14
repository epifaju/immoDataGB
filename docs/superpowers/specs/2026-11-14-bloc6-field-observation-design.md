# Bloc 6.2 — Saisie terrain (thin slice) — Design

Date : 2026-11-14  
Statut : en relecture  
Périmètre : preuve verticale mince (approche A) — `POST /field-observations` sans photos, `validation_status` sur `property`, page `/field/new` + GPS, pas d’approve  
Dépend de : Cycle 0, Blocs 1–6.1 (import CSV)

Ce bloc **ajoute** la colonne `property.validation_status`, l’endpoint de création d’observation terrain (JSON), le filtre carte/liste sur `validated`, et la page Next.js `/[locale]/field/new`.

Il **n’ajoute pas** de photos, de brouillon localStorage, d’endpoint de validation/approve, d’`audit_event`, ni de colonnes prix/surface sur `property`.

## Décisions

- Approche **A** : create-only ; pending exclus de la carte/liste ; fiche GET par id autorisée même en pending.
- Migration **`V12`** (`V11` = imports).
- Colonne **`validation_status`** : `pending_validation` \| `validated`. DEFAULT **`validated`** pour préserver seed et imports CSV déjà visibles.
- Création terrain → toujours `pending_validation`.
- Drapeau **`app.field.mutations-enabled`** : `false` par défaut, `true` en `dev`. POST → 404 `NOT_FOUND` si off.
- Miroir UX : `NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED`.
- Org : `app.field.organisation-id` (UUID seed en `dev`).
- Accès données : **JdbcClient**. Pas de JPA / MapStruct / Bean Validation / Spring Security / JWT.
- Soft-delete inchangé (`deleted_at`).
- Pas de photos ; pas de `estimatedArea` / `observedPrice` en colonnes (hors notes courte via `source.label`).
- Référence auto : `FLD-{yyyyMMdd}-{8 hex}` (UTC via `Clock`).
- Confiance initiale : score **12**, level **low**, summary PT déclaratif fixe ; `doc_status=none`.
- Source : `source_type=field_observation` ; `label` = notes (≤ 200) avec préfixe occupation si fourni.
- Hors scope : photos, localStorage draft, approve, audit, Playwright, IndexedDB.

## Critère de succès

1. Flyway applique `V12` ; biens seed/imports restent `validated` et visibles sur la carte.
2. Avec mutations on : `POST /api/v1/field-observations` JSON valide → **201** + `validationStatus=pending_validation` + geom + source `field_observation`.
3. Le bien créé **n’apparaît pas** dans la liste/carte spatiale ; `GET /api/v1/properties/{id}` → **200**.
4. Drapeau off → POST 404 `NOT_FOUND`.
5. Page `/pt/field/new` : formulaire + GPS (ou saisie manuelle) + succès avec lien fiche ; nav i18n.
6. `./mvnw verify` et `npm test` passent ; Blocs 0–6.1 restent verts.

## Hors de ce bloc

Photos multipart, brouillon localStorage, `POST …/validate`, `audit_event`, JWT, Bean Validation annotations, prix/surface sur `property`, Playwright, file admin (Bloc 7).

## Architecture

```text
POST /api/v1/field-observations (JSON)
        ↓ (si mutations-enabled)
FieldObservationController → parse/validate
        ↓
FieldObservationService
  1. Guard flag + organisationId
  2. Generate reference FLD-…
  3. INSERT property (pending_validation, confidence 12/low)
  4. INSERT property_geometry (point, gps)
  5. INSERT source (field_observation, label)
        ↓
201 ApiResponse<FieldObservationCreated>

PropertySpatialRepository list/map
  … AND validation_status = 'validated'

PropertySpatialRepository findById
  … no validation_status filter (pending OK)
```

Packages :

```text
api.field.FieldObservationController
application.field.FieldObservationService
application.field.FieldObservationRequest
application.field.FieldObservationCreated
application.field.FieldObservationRequestParser
infrastructure.config.FieldProperties
infrastructure.persistence.field.FieldObservationRepository
(+ modify PropertySpatialRepository)
```

## Modèle de données

### `V12__add_property_validation_status.sql`

```sql
ALTER TABLE property
  ADD COLUMN validation_status VARCHAR(30) NOT NULL DEFAULT 'validated';

ALTER TABLE property
  ADD CONSTRAINT property_validation_status_check CHECK (
    validation_status IN ('pending_validation', 'validated')
  );

CREATE INDEX property_validation_status_active_idx
  ON property (validation_status)
  WHERE deleted_at IS NULL;
```

Les lignes existantes reçoivent `validated` via DEFAULT au ADD COLUMN NOT NULL DEFAULT.

### Label source

Si `occupationStatus` présent :  
`[{occupationStatus}] {notes}` tronqué à 200 chars (notes seules si pas d’occupation).  
`occupationStatus` ∈ `occupied` \| `vacant` \| `unknown`.  
Si label vide après trim → `NULL` (contrainte source label).

## API

### Config

```yaml
app:
  field:
    mutations-enabled: false
    organisation-id: 00000000-0000-0000-0000-00000000000a  # dev
```

### Request

| Champ | Règle |
|---|---|
| `latitude` | −90…90 required |
| `longitude` | −180…180 required |
| `propertyType` | `house`\|`apartment`\|`land`\|`commercial` required |
| `title` | optional, ≤ 500 ; défaut PT `Observação de campo ({reference})` |
| `occupationStatus` | optional enum |
| `notes` | optional, contribution au label ≤ 200 total |

Validation manuelle → `FIELD_REQUEST_INVALID` (400).

### Response 201

```json
{
  "data": {
    "id": "…",
    "reference": "FLD-20261114-a1b2c3d4",
    "title": "…",
    "propertyType": "house",
    "validationStatus": "pending_validation",
    "latitude": 11.863,
    "longitude": -15.598,
    "confidenceScore": 12,
    "confidenceLevel": "low"
  }
}
```

### Erreurs

| Code | HTTP |
|---|---|
| `NOT_FOUND` | 404 | mutations off |
| `FIELD_REQUEST_INVALID` | 400 | |
| `FIELD_ORGANISATION_MISSING` | 500 | org null alors que mutations on |

## Filtrage spatial

Toute requête liste/carte de `PropertySpatialRepository` (et équivalents count) ajoute :

```sql
AND p.validation_status = 'validated'
```

`findById` / détail : **pas** ce prédicat.

Imports CSV (Bloc 6.1) : s’appuient sur DEFAULT `validated` — aucun changement obligatoire du writer si la colonne a DEFAULT ; si INSERT explicite de colonnes, ajouter `validation_status` ou laisser DEFAULT DB.

## Frontend

- `app/[locale]/field/new/page.tsx`
- Composants formulaire + résultat succès
- `lib/api/field.ts`
- Messages `field.json` + `nav.field` : pt `Terreno` / fr `Terrain` / en `Field`
- `errors.json` : codes FIELD_*
- Geolocation avec fallback manuel
- Upload/submit visible si `NEXT_PUBLIC_FIELD_MUTATIONS_ENABLED === 'true'`

## Tests

- Parser/service unit
- WebMvc : flag, 201, invalid body
- IT : create pending → absent spatial list ; GET detail 200 ; seed always on map
- Vitest form + geolocation mock
- README : curl POST, flags, page `/field/new`, règle pending hors carte

## Checklist

- [ ] V12 validation_status + DEFAULT validated
- [ ] POST field-observations + drapeau
- [ ] Filtre spatial validated-only ; GET detail pending OK
- [ ] Page /field/new + nav + i18n
- [ ] IT + WebMvc + Vitest
- [ ] README
- [ ] Pas de photos / approve / JWT
