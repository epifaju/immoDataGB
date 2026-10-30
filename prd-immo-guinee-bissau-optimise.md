# PRD Optimisé — Plateforme de Données Immobilières et Foncières — Guinée-Bissau

> **Version** : 2.0 — Optimisé pour développement Cursor AI  
> **Stack cible** : Next.js 14 · Spring Boot 3.3 · PostgreSQL 16/PostGIS · S3-compatible  
> **Langues** : Portugais (défaut) · Français · Anglais  
> **Dernière mise à jour** : 2025

---

## Table des matières

1. [Vue d'ensemble et vision produit](#1-vue-densemble-et-vision-produit)
2. [Contexte et problème](#2-contexte-et-problème)
3. [Stack technique recommandée](#3-stack-technique-recommandée)
4. [Architecture système](#4-architecture-système)
5. [Modèle de données](#5-modèle-de-données)
6. [Modules fonctionnels — MVP](#6-modules-fonctionnels--mvp)
7. [Internationalisation (i18n)](#7-internationalisation-i18n)
8. [Sécurité et RBAC](#8-sécurité-et-rbac)
9. [Exigences non fonctionnelles](#9-exigences-non-fonctionnelles)
10. [Blocs d'implémentation Cursor AI](#10-blocs-dimplémentation-cursor-ai)
11. [Roadmap produit](#11-roadmap-produit)
12. [Risques et mitigations](#12-risques-et-mitigations)
13. [KPIs et critères de lancement](#13-kpis-et-critères-de-lancement)

---

## 1. Vue d'ensemble et vision produit

La plateforme est une **infrastructure numérique de confiance** pour la donnée immobilière et foncière en Guinée-Bissau. Elle n'est pas un simple portail d'annonces : elle agrège, qualifie et rend exploitables des informations dispersées, en affichant explicitement le **niveau de preuve** et le **score de confiance** de chaque donnée.

### Proposition de valeur centrale

> Rendre visibles, traçables et comparables des informations immobilières et foncières dispersées, tout en affichant un score de confiance et un statut de vérification sur chaque donnée sensible.

### Personas cibles

| Persona | Besoin principal | Canal privilégié |
|---|---|---|
| Investisseur / diaspora | Identifier des opportunités, évaluer le risque juridique | Web desktop |
| Agence immobilière | Publier, gérer et valoriser ses dossiers | Back-office web |
| Juriste / notaire | Vérifier la cohérence documentaire, suivre l'historique | Web desktop |
| Agent terrain | Collecter des données GPS + photos sur le terrain | Mobile web |
| ONG / institution | Cartographier les droits, produire des vues spatiales | Web + exports |

---

## 2. Contexte et problème

Contrairement à la France (DVF, cadastre public), la Guinée-Bissau présente :

- Des informations foncières éparses et peu numérisées
- Des chevauchements entre droits formels et usages coutumiers
- L'absence d'un référentiel centralisé vérifiable
- Des litiges fonciers fréquents augmentant le risque opérationnel et juridique

**Conséquence :** agences, investisseurs, juristes, ONG et membres de la diaspora manquent d'un outil unifié pour consulter, comparer et valider les informations immobilières.

---

## 3. Stack technique recommandée

### ✅ Changements recommandés vs. PRD initial

| Composant | PRD initial | Recommandation optimisée | Justification |
|---|---|---|---|
| Frontend | React JS + Tailwind | **Next.js 14** (App Router) + Tailwind CSS + shadcn/ui | SSR/SSG natif, SEO, routing avancé, cohérence avec vos projets existants |
| Cartographie | MapLibre ou Leaflet | **MapLibre GL JS** (prioritaire) + **react-map-gl** | Tiles vectorielles, styles personnalisés, meilleure performance GPU |
| État global | React Query / Zustand | **TanStack Query v5** + Zustand | Cohérence avec vos projets, cache serveur puissant |
| i18n | "Prévu à moyen terme" | **next-intl** dès le Bloc 0 | Multilingue obligatoire dès le départ, sinon refactoring coûteux |
| Carte base | Non spécifié | **OpenStreetMap** + couches OSM Overpass | Gratuit, couvre la Guinée-Bissau correctement |
| Tuiles vectorielles | Non spécifié | **Martin** (Rust, PostgreSQL) | Génère des MVT depuis PostGIS sans coût licence |
| Auth | Spring Security | **Spring Security + JWT (RS256)** + refresh tokens | Stateless, compatible mobile terrain |
| File storage | S3-compatible | **MinIO** (auto-hébergé) ou **Scaleway Object Storage** | Souveraineté des données, coût maîtrisé |
| Search | PostgreSQL only | **PostgreSQL FTS** + `pg_trgm` | Suffisant MVP, upgrade Elasticsearch en phase 2 |
| Jobs async | "Prévu" | **Spring Batch** + **@Scheduled** | Imports lourds, scoring confiance, OCR futur |
| Monitoring | Non spécifié | **Micrometer** + **Grafana/Loki** | Observabilité dès le MVP |

### Stack finale recommandée

```
Frontend
├── Next.js 14 (App Router, TypeScript)
├── Tailwind CSS + shadcn/ui
├── MapLibre GL JS + react-map-gl
├── TanStack Query v5
├── Zustand (état global léger)
├── next-intl (i18n : PT/FR/EN)
├── React Hook Form + Zod (formulaires + validation)
└── Vitest + Playwright (tests)

Backend
├── Java 21 + Spring Boot 3.3
├── Spring Security (JWT RS256 + refresh tokens)
├── Spring Data JPA + Hibernate Spatial
├── Spring Batch (imports asynchrones)
├── Flyway 10 (migrations versionnées)
├── OpenAPI 3 / SpringDoc (documentation auto)
└── JUnit 5 + Testcontainers (tests)

Base de données
├── PostgreSQL 16 + PostGIS 3.4
├── pg_trgm (recherche textuelle floue)
└── Index GIST sur géométries

Infrastructure
├── MinIO (stockage S3-compatible)
├── Martin (serveur tuiles MVT depuis PostGIS)
├── Redis (cache sessions + rate limiting)
├── Docker Compose (développement local)
└── GitHub Actions (CI/CD)
```

---

## 4. Architecture système

### Vue logique

```
┌─────────────────────────────────────────────────────────┐
│                    CLIENT (Browser/Mobile)               │
│  Next.js 14 SSR/CSR │ MapLibre GL JS │ next-intl        │
└─────────────────┬───────────────────────────────────────┘
                  │ HTTPS / REST / JWT
┌─────────────────▼───────────────────────────────────────┐
│                    API GATEWAY (Spring Boot)              │
│  /api/v1/auth  /properties  /parcels  /documents  ...   │
│  Spring Security │ RBAC │ Rate Limiting │ Audit          │
└──────┬──────────┬──────────┬──────────────────┬─────────┘
       │          │          │                  │
  ┌────▼────┐ ┌───▼───┐ ┌───▼────────┐  ┌──────▼─────┐
  │ PostGIS │ │ Redis │ │   MinIO    │  │   Martin   │
  │  PG 16  │ │ Cache │ │ Documents  │  │ MVT Tiles  │
  └─────────┘ └───────┘ └────────────┘  └────────────┘
```

### Flux principaux

**Consultation carte :**
```
Browser → Next.js → GET /api/v1/properties?bbox=&filters=
       → Spring Boot → PostGIS ST_Within → JSON → MapLibre markers
Browser → Martin (tuiles MVT) → MapLibre layers (polygones parcelles)
```

**Upload documentaire :**
```
Browser → POST /api/v1/documents/upload (multipart)
        → Spring Boot [validation + virus scan basique + RBAC]
        → MinIO [stockage chiffré] + PostgreSQL [métadonnées]
        → Audit event enregistré
GET /api/v1/documents/{id}/signed-url → URL signée 15 min
```

**Import CSV/Excel :**
```
Browser → POST /api/v1/imports (fichier + mapping)
        → Spring Batch Job [validation → déduplication → insertion]
        → PostgreSQL import_job + import_row_error
        → Score confiance recalculé en batch
```

**Saisie terrain (mobile) :**
```
Agent → Formulaire Next.js mobile-first
      → POST /api/v1/field-observations [GPS + photos + notes]
      → Statut "pending_validation"
      → Notification admin → Validation back-office → Publication
```

---

## 5. Modèle de données

### Migrations Flyway (ordre d'exécution)

```
V1__create_extensions.sql          -- PostGIS, pg_trgm, uuid-ossp
V2__create_users_roles.sql         -- users, roles, user_roles, permissions
V3__create_admin_boundaries.sql    -- admin_boundary (OSM import)
V4__create_properties_parcels.sql  -- property, parcel, dossier
V5__create_geometry.sql            -- geometry (table séparée)
V6__create_sources.sql             -- source, source_evidence
V7__create_confidence_engine.sql   -- confidence_score, confidence_rule
V8__create_documents_media.sql     -- document, media, document_entity_link
V9__create_comparables.sql         -- comparable_observation, valuation_result
V10__create_imports.sql            -- import_job, import_row_error
V11__create_audit.sql              -- audit_event
V12__create_indexes.sql            -- Index GIST, GIN, pg_trgm
```

### Entités clés

#### `property` — Bien immobilier
```sql
CREATE TABLE property (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organisation_id UUID NOT NULL,                    -- multi-tenant
  reference       VARCHAR(50) UNIQUE NOT NULL,      -- référence interne
  entity_type     VARCHAR(20) NOT NULL,             -- 'property' | 'parcel' | 'dossier'
  title           VARCHAR(500),
  description     TEXT,
  property_type   VARCHAR(50),                      -- 'house' | 'apartment' | 'land' | 'commercial'
  land_use        VARCHAR(50),
  
  -- Localisation
  address_text    TEXT,
  admin_boundary_id UUID REFERENCES admin_boundary(id),
  
  -- Surface
  area_declared   DECIMAL(12,2),
  area_calculated DECIMAL(12,2),
  area_unit       VARCHAR(10) DEFAULT 'm2',
  
  -- Prix
  price_listed    DECIMAL(15,2),
  price_observed  DECIMAL(15,2),
  currency        VARCHAR(3) DEFAULT 'XOF',
  price_date      DATE,
  price_per_sqm   DECIMAL(12,2),
  
  -- Statuts
  commercial_status VARCHAR(30) NOT NULL DEFAULT 'observed',
  -- 'for_sale' | 'rented' | 'sold' | 'observed' | 'archived'
  doc_status      VARCHAR(30) NOT NULL DEFAULT 'none',
  -- 'none' | 'declared' | 'partial' | 'verified' | 'litigious'
  
  -- Confiance
  confidence_score SMALLINT CHECK (confidence_score BETWEEN 0 AND 100),
  confidence_level VARCHAR(10),                     -- 'low' | 'medium' | 'high'
  confidence_summary TEXT,
  
  -- Audit
  created_by      UUID REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at    TIMESTAMPTZ,
  is_deleted      BOOLEAN NOT NULL DEFAULT false,
  deleted_at      TIMESTAMPTZ
);
```

#### `geometry` — Géométries séparées
```sql
CREATE TABLE property_geometry (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id     UUID NOT NULL REFERENCES property(id),
  geom            GEOMETRY(Geometry, 4326),         -- point ou polygone
  geom_type       VARCHAR(20),                      -- 'point' | 'polygon' | 'approximate'
  precision_level VARCHAR(20),                      -- 'gps' | 'address' | 'zone'
  source_id       UUID REFERENCES source(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_property_geometry_gist ON property_geometry USING GIST(geom);
```

#### `source` — Sources et traçabilité
```sql
CREATE TABLE source (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id     UUID REFERENCES property(id),
  source_type     VARCHAR(30) NOT NULL,
  -- 'agency_listing' | 'field_observation' | 'scanned_doc' | 'institutional'
  -- 'third_party_user' | 'partner' | 'open_layer'
  contributor_id  UUID REFERENCES users(id),
  acquisition_method VARCHAR(50),
  observed_at     DATE,
  raw_data        JSONB,                            -- données brutes originales
  weight          DECIMAL(3,2) DEFAULT 1.0,         -- poids dans le scoring
  is_verified     BOOLEAN DEFAULT false,
  verified_by     UUID REFERENCES users(id),
  verified_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### `confidence_rule` — Moteur de confiance configurable
```sql
CREATE TABLE confidence_rule (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organisation_id UUID NOT NULL,
  rule_name       VARCHAR(100) NOT NULL,
  rule_type       VARCHAR(50) NOT NULL,
  -- 'source_count' | 'source_type_bonus' | 'doc_verified_bonus'
  -- 'inconsistency_penalty' | 'age_penalty'
  condition_json  JSONB NOT NULL,                   -- conditions d'application
  score_delta     SMALLINT NOT NULL,                -- +/- points
  is_active       BOOLEAN DEFAULT true,
  priority        SMALLINT DEFAULT 0
);
```

#### `audit_event` — Journal d'audit
```sql
CREATE TABLE audit_event (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_type     VARCHAR(50) NOT NULL,
  entity_id       UUID NOT NULL,
  action          VARCHAR(50) NOT NULL,
  -- 'create' | 'update' | 'delete' | 'verify' | 'upload' | 'download'
  -- 'status_change' | 'import' | 'validate' | 'reject'
  performed_by    UUID REFERENCES users(id),
  ip_address      INET,
  old_value       JSONB,
  new_value       JSONB,
  summary         TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_event(created_at DESC);
```

---

## 6. Modules fonctionnels — MVP

### Module 1 — Cartographie et recherche spatiale

**Endpoints backend :**
```
GET  /api/v1/properties?bbox={w,s,e,n}&type=&status=&confidence=&page=&size=
GET  /api/v1/properties/{id}
GET  /api/v1/properties/{id}/geometry
GET  /api/v1/layers                         -- couches SIG disponibles
GET  /api/v1/layers/{id}/geojson            -- GeoJSON d'une couche
GET  /api/v1/admin-boundaries               -- limites administratives
```

**Logique PostGIS :**
```sql
-- Requête spatiale principale (avec pagination)
SELECT p.*, ST_AsGeoJSON(pg.geom) as geometry
FROM property p
LEFT JOIN property_geometry pg ON pg.property_id = p.id
WHERE ST_Within(pg.geom, ST_MakeEnvelope(:west, :south, :east, :north, 4326))
  AND p.commercial_status = ANY(:statuses)
  AND p.confidence_level = ANY(:confidence_levels)
  AND p.is_deleted = false
ORDER BY p.created_at DESC
LIMIT :size OFFSET :offset;
```

**Frontend Next.js :**
- `app/[locale]/map/page.tsx` — layout carte + liste synchronisée
- `components/map/PropertyMap.tsx` — composant MapLibre GL JS
- `components/map/PropertyMarker.tsx` — cluster ou point selon zoom
- `components/map/FilterPanel.tsx` — filtres dynamiques
- URL persistante avec paramètres : `/map?bbox=...&type=land&confidence=high`

**Critères d'acceptation :**
- [ ] Carte affiche biens géolocalisés avec clusters au zoom faible
- [ ] Filtres mis à jour en < 300ms (debounce TanStack Query)
- [ ] URL partageable avec filtres et emprise
- [ ] Clic sur marqueur ouvre la fiche (drawer ou navigation)
- [ ] Réponse API spatiale < 2s sur périmètre pilote Bissau

---

### Module 2 — Fiche bien / parcelle

**Endpoints backend :**
```
GET    /api/v1/properties/{id}              -- fiche complète
GET    /api/v1/properties/{id}/sources      -- sources liées
GET    /api/v1/properties/{id}/documents    -- documents (métadonnées)
GET    /api/v1/properties/{id}/history      -- historique modifications
GET    /api/v1/properties/{id}/comparables  -- comparables proches
POST   /api/v1/properties                   -- créer (Agent/Partenaire)
PUT    /api/v1/properties/{id}              -- modifier (selon rôle)
DELETE /api/v1/properties/{id}             -- suppression logique (Admin)
POST   /api/v1/admin/properties/{id}/verify -- valider (Analyste/Admin)
POST   /api/v1/admin/properties/{id}/reject -- rejeter avec motif
```

**Response wrapper standard :**
```java
public record ApiResponse<T>(
  boolean success,
  T data,
  String message,
  Map<String, Object> meta  // pagination, confidence, etc.
) {}
```

**Critères d'acceptation :**
- [ ] Badge visuel clair : données déclarées vs vérifiées
- [ ] Score de confiance affiché avec justification textuelle
- [ ] Historique consultable selon le rôle utilisateur
- [ ] Galerie médias avec prévisualisation

---

### Module 3 — Moteur de confiance

**Logique de scoring (configurable via `confidence_rule`) :**

| Règle | Delta score |
|---|---|
| Source unique non vérifiée | 20 pts |
| + 2e source cohérente | +15 pts |
| Source terrain (GPS + photos) | +20 pts |
| Document scanné déclaré | +10 pts |
| Document vérifié par analyste | +25 pts |
| Validation interne complète | +10 pts |
| Incohérence surface/prix | -20 pts |
| Source > 24 mois | -5 pts |
| Litige documentaire | -30 pts |

**Service Java :**
```java
// NOUVEAU fichier — ne modifie pas les services existants
@Service
@Transactional(readOnly = true)
public class ConfidenceEngineService {
    public ConfidenceResult computeScore(UUID propertyId) { ... }
    public void recalculateAllScores(UUID organisationId) { ... }  // batch
}
```

**Critères d'acceptation :**
- [ ] Score recalculé automatiquement à chaque modification de source/document
- [ ] Justification textuelle générée automatiquement
- [ ] Badge coloré : Rouge (0-33) · Orange (34-66) · Vert (67-100)
- [ ] Rules configurables depuis le back-office sans redéploiement

---

### Module 4 — Estimation par comparables

**Endpoint :**
```
POST /api/v1/valuations/estimate
Body: {
  propertyType: string,
  area: number,
  latitude: number,
  longitude: number,
  radiusMeters: number,
  periodMonths: number
}

Response: {
  rangeLow: number,
  rangeHigh: number,
  median: number,
  pricePerSqm: number,
  comparablesCount: number,
  confidenceLevel: string,
  confidenceScore: number,
  warning: string | null,   -- OBLIGATOIRE si échantillon < 5
  comparables: Comparable[] -- liste des comparables utilisés
}
```

**Règles métier (non négociables) :**
- Méthode : médiane + IQR sur les comparables filtrés
- Exclusion automatique des valeurs aberrantes (IQR × 1.5)
- **Interdit** d'afficher une valeur ponctuelle sans fourchette
- **Obligatoire** : avertissement si n < 5 comparables
- Score de confiance de l'estimation lié à n et à la qualité des comparables

---

### Module 5 — Gestion documentaire

**Endpoints :**
```
POST   /api/v1/documents/upload              -- upload (multipart, max 50MB)
GET    /api/v1/documents/{id}               -- métadonnées
POST   /api/v1/documents/{id}/signed-url    -- URL signée (15 min)
PUT    /api/v1/documents/{id}/status        -- changer statut vérification
DELETE /api/v1/documents/{id}              -- suppression logique + audit
GET    /api/v1/documents/{id}/history       -- historique accès
```

**Sécurité documentaire :**
```java
// Validation upload
- Extension whitelist : pdf, jpg, jpeg, png, webp, heic, docx, xlsx
- Taille max : 50 MB
- Scan MIME type (Apache Tika)
- URL signée MinIO : expiration 15 minutes
- Chiffrement au repos : SSE-S3 MinIO
- Audit : chaque upload, consultation et suppression logique
```

**Types documentaires :**
```
titre_foncier | attestation_possession | contrat_vente | contrat_location
plan_parcelle | photo_terrain | piece_identite_masquee | acte_notarie
document_administratif | autre
```

---

### Module 6 — Ingestion de données

#### 6.1 Import CSV/Excel (Spring Batch)

```java
// Job steps séquentiels :
// 1. ReadStep     — lecture fichier + mapping colonnes configuré
// 2. ValidateStep — champs obligatoires, types, formats
// 3. DedupeStep   — rapprochement avec biens existants (pg_trgm + PostGIS)
// 4. WriteStep    — insertion en base avec source = 'csv_import'
// 5. ReportStep   — génération rapport JSON (succès/erreurs/avertissements)

POST /api/v1/imports
Response: { jobId: UUID, status: "processing" }

GET /api/v1/imports/{jobId}/status
GET /api/v1/imports/{jobId}/report
```

#### 6.2 Saisie terrain mobile

```
POST /api/v1/field-observations
Body: {
  latitude: number,
  longitude: number,
  propertyType: string,
  occupationStatus: string,
  estimatedArea: number,
  observedPrice: number,
  notes: string,
  photos: File[],          -- multipart
  sourceType: "field_observation"
}
→ Statut initial : pending_validation
→ Notification admin via audit_event
```

**UX mobile terrain :**
- Formulaire Next.js `app/[locale]/field/new/page.tsx`
- `navigator.geolocation` pour capture GPS automatique
- Upload photos progressif avec prévisualisation
- Bouton "Sauvegarder brouillon" (localStorage → sync dès réseau disponible, Phase 2 pour IndexedDB complet)

---

### Module 7 — Back-office et administration

**Routes Next.js :**
```
/[locale]/admin/dashboard          -- KPIs + activité temps réel
/[locale]/admin/validation-queue   -- file de validation terrain
/[locale]/admin/imports            -- gestion imports + rapports
/[locale]/admin/properties         -- liste + modération
/[locale]/admin/users              -- gestion utilisateurs et rôles
/[locale]/admin/layers             -- couches SIG (upload GeoJSON)
/[locale]/admin/confidence-rules   -- configuration moteur de confiance
/[locale]/admin/audit              -- journal d'audit filtrable
```

---

## 7. Internationalisation (i18n)

### Configuration next-intl

```typescript
// next.config.ts
import createNextIntlPlugin from 'next-intl/plugin';
const withNextIntl = createNextIntlPlugin('./i18n/request.ts');

// Structure des locales
export const locales = ['pt', 'fr', 'en'] as const;
export const defaultLocale = 'pt';           // Portugais par défaut
export type Locale = typeof locales[number];
```

### Structure des fichiers de traduction

```
messages/
├── pt/                    -- Portugais (défaut)
│   ├── common.json
│   ├── map.json
│   ├── property.json
│   ├── documents.json
│   ├── admin.json
│   └── errors.json
├── fr/                    -- Français
│   └── [même structure]
└── en/                    -- Anglais
    └── [même structure]
```

### Préférence utilisateur

```sql
-- Colonne dans la table users
ALTER TABLE users ADD COLUMN preferred_locale VARCHAR(5) DEFAULT 'pt';
```

```typescript
// Priorité de résolution de la locale :
// 1. Préférence du profil utilisateur (si connecté)
// 2. Cookie `NEXT_LOCALE`
// 3. Accept-Language header
// 4. defaultLocale = 'pt'
```

### Règles importantes

- Toutes les clés de traduction définies en portugais en premier
- Les labels cartographiques (badges confiance, statuts) traduits dans les 3 langues
- Les e-mails de notification : langue préférée du destinataire
- Les exports PDF/Excel : langue de l'utilisateur qui exporte
- Les messages d'erreur API (`BusinessException`) : codes string traduits côté frontend

---

## 8. Sécurité et RBAC

### Matrice des permissions

| Action | Admin | Analyste | Agent terrain | Partenaire agence | Lecteur pro | Lecteur public |
|---|---|---|---|---|---|---|
| Voir biens publiés | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (limité) |
| Voir docs sensibles | ✅ | ✅ | ❌ | Ses biens | ✅ | ❌ |
| Créer observation terrain | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Publier bien | ✅ | ✅ | ❌ | Ses biens | ❌ | ❌ |
| Valider / rejeter | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Importer CSV | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| Gérer utilisateurs | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Config moteur confiance | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Journal d'audit | ✅ | ✅ (partiel) | ❌ | ❌ | ❌ | ❌ |

### Implémentation Spring Security

```java
// Annotations sur les controllers (NOUVEAU controller par module)
@PreAuthorize("hasRole('ADMIN') or hasRole('ANALYSTE')")
@PreAuthorize("hasRole('ADMIN') or (hasRole('PARTENAIRE') and @securityService.isOwner(#id, authentication))")
@PreAuthorize("isAuthenticated()")
```

### JWT RS256

```yaml
# application.yml
security:
  jwt:
    private-key: classpath:keys/private.pem
    public-key: classpath:keys/public.pem
    access-token-expiry: 15m
    refresh-token-expiry: 7d
```

---

## 9. Exigences non fonctionnelles

### Performance

| Métrique | Cible MVP | Méthode de mesure |
|---|---|---|
| Chargement initial carte | < 3s (3G correct) | Lighthouse |
| API endpoints simples | < 200ms (p95) | Micrometer + Grafana |
| Requêtes spatiales (bbox) | < 2s (p95) | PostGIS EXPLAIN ANALYZE |
| Upload document 10MB | < 5s | Tests E2E Playwright |
| Import 1000 lignes CSV | < 30s | Spring Batch metrics |

### Pagination obligatoire

```java
// Tous les endpoints liste utilisent PageResponse<T>
public record PageResponse<T>(
  List<T> content,
  int page,
  int size,
  long total,
  int totalPages
) {}
// Default : page=0, size=20, max=100
```

### Scalabilité base de données

```sql
-- Index obligatoires (V12__create_indexes.sql)
CREATE INDEX CONCURRENTLY idx_property_org ON property(organisation_id);
CREATE INDEX CONCURRENTLY idx_property_status ON property(commercial_status, is_deleted);
CREATE INDEX CONCURRENTLY idx_property_confidence ON property(confidence_level);
CREATE INDEX CONCURRENTLY idx_property_trgm ON property USING GIN(to_tsvector('portuguese', title || ' ' || COALESCE(address_text, '')));
CREATE INDEX CONCURRENTLY idx_audit_entity ON audit_event(entity_type, entity_id, created_at DESC);
```

---

## 10. Blocs d'implémentation Cursor AI

> Chaque bloc est **indépendant et non-régressif**. Les blocs suivants n'écrasent jamais les fichiers des blocs précédents.

### Bloc 0 — Fondations et infrastructure

**Objectif :** Initialiser le projet complet, configurer i18n, sécurité, Flyway, Docker Compose.

**Livrables :**
- Next.js 14 initialisé avec App Router + TypeScript + Tailwind + shadcn/ui
- next-intl configuré (PT défaut / FR / EN), structure `messages/` créée
- Routing `app/[locale]/` avec middleware de détection locale
- Spring Boot 3.3 + Java 21, structure packages `domain/application/infrastructure/api`
- Flyway V1–V2 (extensions PostGIS + users/roles)
- Docker Compose : PostgreSQL 16 + PostGIS, MinIO, Redis, Martin, app backend
- `.env.example` documenté, `application.yml` multi-profil (dev/staging/prod)
- CI GitHub Actions : lint + tests unitaires backend + build frontend

**Prompt Cursor AI (exemple) :**
```
Initialise un projet Next.js 14 avec App Router, TypeScript strict, Tailwind CSS, 
shadcn/ui et next-intl configuré pour 3 locales ['pt','fr','en'] avec 'pt' 
comme defaultLocale. Structure le routing sous app/[locale]/ avec un middleware 
i18n. Crée les fichiers messages/pt/common.json, messages/fr/common.json, 
messages/en/common.json avec les clés : nav.map, nav.properties, nav.admin, 
common.loading, common.error, common.save, common.cancel.
```

---

### Bloc 1 — Cartographie interactive

**Objectif :** Carte MapLibre + endpoints PostGIS + filtres dynamiques.

**Nouveaux fichiers uniquement :**
- `V3__create_admin_boundaries.sql`, `V4__create_properties_parcels.sql`, `V5__create_geometry.sql`
- `PropertyController.java` (GET /properties avec bbox)
- `PropertySpatialRepository.java` (requêtes PostGIS natives)
- `app/[locale]/map/page.tsx`
- `components/map/PropertyMap.tsx`, `FilterPanel.tsx`, `PropertyMarker.tsx`

**Ne modifie pas :** les fichiers du Bloc 0.

---

### Bloc 2 — Fiches biens et CRUD

**Objectif :** CRUD complet fiches property/parcel/dossier + workflow validation.

**Nouveaux fichiers uniquement :**
- `V6__create_sources.sql`
- `PropertyDetailController.java`, `PropertyService.java`, `PropertyMapper.java`
- `app/[locale]/properties/[id]/page.tsx`
- `components/property/PropertyDetail.tsx`, `ConfidenceBadge.tsx`, `SourceList.tsx`

---

### Bloc 3 — Moteur de confiance

**Objectif :** Scoring configurable + recalcul automatique.

**Nouveaux fichiers uniquement :**
- `V7__create_confidence_engine.sql`
- `ConfidenceEngineService.java` (nouveau service isolé)
- `ConfidenceRuleRepository.java`
- `ConfidenceController.java` (admin)
- `components/admin/ConfidenceRuleEditor.tsx`

---

### Bloc 4 — Gestion documentaire

**Objectif :** Upload MinIO + métadonnées + URLs signées + audit.

**Nouveaux fichiers uniquement :**
- `V8__create_documents_media.sql`
- `DocumentController.java`, `DocumentService.java`, `MinioStorageService.java`
- `components/documents/DocumentUpload.tsx`, `DocumentViewer.tsx`

---

### Bloc 5 — Estimation par comparables

**Objectif :** Moteur estimation fourchette + score confiance estimation.

**Nouveaux fichiers uniquement :**
- `V9__create_comparables.sql`
- `ValuationController.java`, `ValuationService.java` (IQR + avertissements)
- `app/[locale]/valuation/page.tsx`
- `components/valuation/EstimationForm.tsx`, `EstimationResult.tsx`

---

### Bloc 6 — Ingestion de données

**Objectif :** Import CSV/Excel (Spring Batch) + saisie terrain mobile.

**Nouveaux fichiers uniquement :**
- `V10__create_imports.sql`
- `ImportController.java`, `ImportBatchConfig.java`, `ImportJobLauncher.java`
- `FieldObservationController.java`, `FieldObservationService.java`
- `app/[locale]/field/new/page.tsx` (formulaire mobile-first GPS)
- `app/[locale]/admin/imports/page.tsx`

---

### Bloc 7 — Back-office et journal d'audit

**Objectif :** Tableau de bord admin, file de validation, journal d'audit filtrable.

**Nouveaux fichiers uniquement :**
- `V11__create_audit.sql`
- `AuditService.java` (aspect Spring AOP pour journalisation transversale)
- `AdminController.java`, `AuditController.java`
- `app/[locale]/admin/dashboard/page.tsx`
- `app/[locale]/admin/validation-queue/page.tsx`
- `app/[locale]/admin/audit/page.tsx`

---

### Bloc 8 — Profil utilisateur et préférences de langue

**Objectif :** Gestion profil + préférence locale persistée en base.

**Nouveaux fichiers uniquement :**
- `UserProfileController.java`, `UserProfileService.java`
- `app/[locale]/profile/page.tsx`
- `components/profile/LanguagePicker.tsx` (sélecteur PT/FR/EN)
- Middleware Next.js mis à jour pour lire la préférence depuis l'API au login

---

## 11. Roadmap produit

### Phase 1 — MVP pilote Bissau (semaines 1–16)
Blocs 0 à 8 tels que définis ci-dessus.

### Phase 2 — Industrialisation (mois 5–9)
- Déduplication avancée (embedding sémantique léger)
- Mode offline partiel pour terrain (Service Worker + IndexedDB)
- Exports PDF/Excel des fiches et analyses
- Reporting avancé avec graphiques de marché
- Partage de collections et vues sauvegardées

### Phase 3 — OCR et intelligence documentaire (mois 10–15)
- OCR documents fonciers (Tesseract + OCRmyPDF, réutiliser l'expérience archivage)
- Extraction assistée de champs candidats
- Contrôles de cohérence automatiques
- Suggestions de rapprochement document ↔ bien ↔ parcelle

### Phase 4 — Plateforme étendue (mois 16+)
- Extension géographique hors Bissau
- API partenaires (REST + webhooks)
- Portail institutionnel multi-organisations
- Séries temporelles de marché et indicateurs macro

---

## 12. Risques et mitigations

| Risque | Impact | Mitigation |
|---|---|---|
| Données foncières fragmentées | Élevé | Modèle centré source + score de confiance dès le Bloc 0 |
| Faible qualité des imports | Élevé | Validation Spring Batch + rapport d'erreurs détaillé |
| Litiges fonciers | Élevé | Statuts de preuve explicites + disclaimers légaux + validation humaine obligatoire |
| Données personnelles sensibles | Élevé | RBAC strict + URLs signées courtes + audit complet |
| Couverture géographique initiale incomplète | Moyen | Zone pilote Bissau strictement délimitée |
| Réseau faible agents terrain | Moyen | UX légère Next.js + brouillons localStorage + offline Phase 2 |
| Mauvaise interprétation des estimations | Élevé | Fourchettes obligatoires + avertissements + score confiance estimation |
| Surcoût infrastructure | Moyen | MinIO auto-hébergé + Martin (gratuit) + PostgreSQL seul en Phase 1 |

---

## 13. KPIs et critères de lancement

### KPIs produit hebdomadaires

- Nouvelles fiches créées / semaine
- Taux de fiches géolocalisées avec précision GPS (vs. adresse textuelle)
- Taux de fiches avec ≥ 1 document joint
- Taux de fiches avec statut "vérifié"
- Nombre d'estimations générées
- Temps moyen de validation d'une observation terrain (h)
- Imports réussis / total imports tentés
- Revenus récurrents mensuels B2B

### Indicateurs de succès Phase 1

| Indicateur | Cible pilote |
|---|---|
| Biens/parcelles géolocalisés | 10 000+ |
| Fiches avec ≥ 1 source structurée | 80 % |
| Fiches avec document joint | 40 % |
| Délai moyen publication après ingestion | < 24 h |
| Temps réponse API spatiale (p95) | < 2 s |
| Utilisation module estimation | 30 % users actifs |

### Critères de lancement MVP (checklist)

- [ ] Carte affiche biens/parcelles sur zone pilote Bissau
- [ ] Imports CSV/Excel stables avec rapports d'erreur exploitables
- [ ] Agents terrain peuvent créer des observations depuis mobile
- [ ] Fiches affichent clairement statuts sources, documents et score confiance
- [ ] Stockage documentaire sécurisé avec RBAC et URLs signées
- [ ] Back-office permet validation, rejet et consultation audit
- [ ] Moteur d'estimation retourne fourchette + avertissement si échantillon faible
- [ ] i18n opérationnel en PT/FR/EN avec préférence profil persistée
- [ ] Journal d'audit couvre toutes les actions critiques
- [ ] Tests E2E passants sur les flux principaux (Playwright)
- [ ] Documentation API OpenAPI complète et accessible

---

*Ce PRD est structuré pour être consommé directement par Cursor AI en mode Agent. Chaque bloc d'implémentation (section 10) est conçu pour être transformé en un prompt Cursor AI autonome, non-régressif et précis.*
