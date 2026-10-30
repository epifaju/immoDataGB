# Cycle 0 — Fondations — Design

Date : 2026-10-30  
Statut : en relecture (révision 2)  
Périmètre : preuve verticale mince, sans authentification

## Décisions

- Le cycle 0 prouve que Docker, Spring Boot et Next.js fonctionnent ensemble. Il ne crée ni utilisateur, ni JWT, ni page métier.
- PostGIS, Redis, MinIO et Martin tournent dans Docker Compose. Spring Boot et Next.js tournent sur la machine, avec rechargement à chaud.
- Le port hôte de PostGIS est `127.0.0.1:5435`. `5432` est pris par PostgreSQL 13, `5433` par `govsuite-postgres`, `5434` par `docuforge-postgres`. Dans le réseau Compose, PostGIS écoute toujours sur `5432`.
- L'API expose un seul endpoint, `GET /api/v1/health`, qui vérifie PostGIS. Redis, MinIO et Martin démarrent, et l'application Java ne s'y connecte pas.
- Martin utilise l'image stable `ghcr.io/maplibre/martin:1.16.1`. Le catalogue reste vide tant qu'aucune table géométrique n'existe.
- Les images officielles MinIO ne sont plus téléchargeables anonymement : Docker Hub les a retirées, et Quay répond 401. Le cycle 0 utilise le fork communautaire `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`, qui contient le serveur et le client `mc`. Le port 9001 n'est pas publié. Le bucket est vérifié par `mc`. L'existence exacte de ce tag est à confirmer à la génération (voir « Points à vérifier à la génération »).
- Il n'y a pas de dépôt Git pour l'instant. Ce cycle ne contient pas de workflow GitHub Actions. Les fichiers `.gitignore` et `.dockerignore` sont créés quand même, pour que le futur dépôt démarre propre.
- Le build backend est Maven, avec le wrapper. Le parent est Spring Boot 3.3, en version exacte : la plus récente 3.3 publiée sur Maven Central le jour de la génération du projet, écrite en littéral dans `pom.xml`. La version retenue est notée dans le `README.md`.
- Next.js reste en version 14, patch 14.2 exact, écrit en littéral dans `package.json`. Le patch retenu est noté dans le `README.md`.
- Runtimes : Java 21 (`java.version` dans `pom.xml`) et Node 20 LTS (champ `engines` de `package.json` et fichier `.nvmrc`).
- Spring Boot 3.3 et Next.js 14 ne reçoivent plus de correctifs de sécurité en open source. Ce choix est assumé pour un cycle 0 local. La montée de version est à planifier avant tout déploiement.
- Versions compatibles avec Next.js 14.2, écrites en littéral : `next-intl` 3.x et Tailwind CSS 3.x (avec la version de shadcn/ui qui cible Tailwind 3).

## Critère de succès

Depuis une machine vide de conteneurs du projet :

1. `docker compose up -d --wait` rend PostGIS, Redis et MinIO sains, crée le bucket `documents`, et Martin répond `OK` sur `/health`.
2. `./mvnw spring-boot:run` applique Flyway et écoute sur le port 8080.
3. `npm run dev` sert `http://localhost:3000/pt`.
4. La page affiche l'état de l'API, de la base et la version PostGIS, en portugais.
5. `/fr` et `/en` affichent les mêmes informations dans leur langue (vérification manuelle, voir « Contrôle manuel »).
6. `./mvnw verify` (backend) et `npm test` (frontend, `vitest run`) passent.

## Hors de ce cycle

Utilisateurs, rôles, JWT, CI, JPA, SpringDoc, client Redis, client MinIO, couches de tuiles, pages carte, fiche, admin, Playwright.

Les packages `domain` et `application` apparaissent avec le premier module métier.

## Démarrage

```text
cp .env.example .env
cp frontend/.env.example frontend/.env.local
docker compose up -d --wait
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

Ouvrir `http://localhost:3000/pt`.

`--wait` évite de lancer le backend avant que PostGIS soit sain. Son comportement avec `minio-init`, qui se termine volontairement avec succès, est à vérifier à la génération. À défaut, attendre l'état `healthy` des trois services durables avant de lancer le backend, et le documenter dans le `README.md`.

La racine `.env` alimente Compose. Next.js lit seulement `frontend/.env.local`. Spring Boot ne lit pas le fichier `.env` : `application-dev.yml` reprend les mêmes valeurs locales, avec un commentaire qui renvoie à `.env` pour éviter toute divergence. Le profil actif par défaut est `dev`, déclaré dans `application.yml`, donc `./mvnw spring-boot:run` suffit. Ce défaut est réservé au développement local et doit disparaître avant tout déploiement.

## Configuration locale

Ces valeurs sont réservées au développement local.

| Usage | Variable | Valeur |
|---|---|---|
| Base PostgreSQL | `POSTGRES_DB` | `immodata` |
| Utilisateur PostgreSQL | `POSTGRES_USER` | `immodata` |
| Mot de passe PostgreSQL | `POSTGRES_PASSWORD` | `immodata` |
| JDBC (`application-dev.yml`) | — | `jdbc:postgresql://localhost:5435/immodata` |
| Utilisateur MinIO | `MINIO_ROOT_USER` | `minioadmin` |
| Mot de passe MinIO | `MINIO_ROOT_PASSWORD` | `minioadmin` |
| URL API navigateur (`frontend/.env.local`) | `NEXT_PUBLIC_API_URL` | `http://localhost:8080` |
| Martin, réseau Compose | — | `postgres://immodata:immodata@postgis:5432/immodata` |

Le fichier `.env.example` contient les variables `POSTGRES_*` et `MINIO_ROOT_*`. Le fichier `frontend/.env.example` contient `NEXT_PUBLIC_API_URL`. Les deux sont destinés à être versionnés. `.env` et `frontend/.env.local` sont ignorés par Git.

## Architecture

```text
Navigateur :3000
  → Next.js (hôte)
  → GET http://localhost:8080/api/v1/health
  → Spring Boot (hôte)
  → JDBC localhost:5435
  → PostGIS (Compose)

Compose, sans appel depuis l'application Java :
  postgis:5432  redis:6379  minio:9000  martin:3001→3000
  minio-init crée le bucket documents puis s'arrête
```

| Service | Image | Port hôte |
|---|---|---|
| postgis | `postgis/postgis:16-3.4` | 127.0.0.1:5435 |
| redis | `redis:7.4-alpine` | 127.0.0.1:6379 |
| minio | `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` | 127.0.0.1:9000 |
| martin | `ghcr.io/maplibre/martin:1.16.1` | 127.0.0.1:3001 |
| backend | processus hôte | 8080 |
| frontend | processus hôte | 3000 |

Tous les ports publiés par Compose sont liés à `127.0.0.1`. Redis n'a pas de mot de passe et MinIO utilise des identifiants par défaut : aucun des deux ne doit être joignable depuis le réseau.

Volumes nommés : `postgis-data`, `minio-data`. Les services durables redémarrent avec `unless-stopped`. `minio-init` ne redémarre pas. Le fichier Compose n'a pas de clé `version` (obsolète).

Healthchecks :

- PostGIS : `pg_isready -U immodata -d immodata`
- Redis : `redis-cli ping`
- MinIO : `GET /minio/health/live` sur le port 9000, avec `curl` si l'image le fournit, sinon avec `mc`
- Martin : `GET /health` sur le port interne 3000. L'image `1.16.1` contient `wget` et pas `curl`, donc le healthcheck Compose utilise `wget`. Martin ne publie que le schéma `public`, pour laisser hors du catalogue les tables Tiger créées par l'image PostGIS.
- `minio-init` attend MinIO sain, puis se termine avec succès

Martin attend PostGIS sain. Il démarre avec `--config /config/martin.yaml`, qui ne publie que le schéma public. Sans table géométrique, `GET http://localhost:3001/catalog` répond 200 et une liste de sources vide. `GET http://localhost:3001/health` répond 200 et le corps `OK`.

`minio-init` utilise la même image. Son entrypoint est `/bin/sh /minio-init.sh`, avec `MC_CONFIG_DIR=/tmp/mc`. Le script n'appelle que `mc`. Il exécute :

```text
mc alias set local http://minio:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD
mc mb --ignore-existing local/documents
mc ls local/documents
```

La commande du serveur est `server /data`, sans port de console.

## Backend

Racine : `backend/`. Package : `com.immodata.gb`. Java 21.

Dépendances : `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `flyway-core`, `flyway-database-postgresql`, pilote PostgreSQL. Pas d'Actuator : le seul contrat de santé est `/api/v1/health`. Dépendances de test : `spring-boot-starter-test`, Testcontainers (PostgreSQL, JUnit 5) et `spring-boot-testcontainers`.

Fichiers :

- `ImmoDataApplication`
- `api/ApiResponse.java`, record `success`, `data`, `message`, `meta`
- `api/health/HealthController.java`
- `api/health/HealthView.java`, record `api`, `database`, `postgis`
- `api/error/ApiExceptionHandler.java`
- `infrastructure/health/PostgisHealthProbe.java`
- `infrastructure/web/CorsConfig.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/db/migration/V1__enable_spatial_extensions.sql`

`ApiResponse.meta` est un objet. Sur cet endpoint il est vide, sérialisé `{}`. `message` est sérialisé même lorsqu'il vaut `null` : aucune configuration Jackson globale n'exclut les valeurs nulles.

`HealthController` répond avec l'en-tête `Cache-Control: no-store`, y compris pour les réponses d'erreur produites par `ApiExceptionHandler`.

`PostgisHealthProbe` exécute `SELECT postgis_version()`. Une base accessible produit `database = "up"` et le texte renvoyé par PostGIS. Toute `DataAccessException` remonte au gestionnaire d'erreurs, y compris une erreur de requête : elle est traitée comme « base indisponible ».

Délai d'échec : `application-dev.yml` fixe `spring.datasource.hikari.connection-timeout` à 3000 ms, pour qu'un 503 arrive en quelques secondes et non après le délai par défaut de 30 s.

`CorsConfig` enregistre un `CorsFilter` à priorité maximale, afin que les réponses 503 et 500 portent aussi l'en-tête d'origine. L'origine vient de `app.cors.allowed-origins`. En dev : `http://localhost:3000`. Le filtre autorise les méthodes `GET` et `OPTIONS`, l'en-tête `Accept`, et n'autorise pas les credentials.

Migration V1 :

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

Cette migration fonctionne en local parce que l'utilisateur `immodata` est superuser dans l'image PostGIS. Hors développement local, les droits sur `CREATE EXTENSION` sont à revoir. PostgreSQL 16 fournit `gen_random_uuid()` sans extension supplémentaire.

## Frontend

Racine : `frontend/`. Node 20 LTS. TypeScript strict, Tailwind 3, shadcn/ui (`Card`), TanStack Query v5, `next-intl` 3.x. Le sélecteur de langue est composé de liens, pas d'un bouton.

Fichiers :

- `middleware.ts`
- `i18n/routing.ts` : locales `pt`, `fr`, `en`, locale par défaut `pt`, préfixe toujours présent
- `i18n/navigation.ts` : `Link` et utilitaires de navigation liés au routage
- `i18n/request.ts` : charge et fusionne `common.json` et `errors.json` de la locale
- `app/[locale]/layout.tsx`
- `app/[locale]/page.tsx`
- `components/providers/QueryProvider.tsx`
- `components/health/HealthStatus.tsx`
- `components/locale/LocaleSwitcher.tsx`
- `lib/api/types.ts`
- `lib/api/health.ts`
- `messages/{pt,fr,en}/common.json`
- `messages/{pt,fr,en}/errors.json`

Le layout est un composant serveur. Il déclare `<html lang={locale}>`, enveloppe les enfants avec `QueryProvider` et affiche le nom de l'application plus `LocaleSwitcher`. Aucun lien vers la carte ou l'administration.

`lib/api/health.ts` appelle `GET ${NEXT_PUBLIC_API_URL}/api/v1/health`. Il lit le corps JSON quel que soit le statut HTTP : `fetch` ne lève pas d'exception sur un 503. Il renvoie le statut HTTP et le corps typé `ApiResponse<HealthView>`. Il lève une erreur seulement si la requête échoue (réseau, CORS) ou si le corps n'est pas du JSON.

`HealthStatus` est un composant client. `useQuery` utilise `lib/api/health.ts` au chargement, avec `retry: false` et `refetchOnWindowFocus: false`, sans rechargement périodique.

Locales : `pt`, `fr`, `en`. Préfixe toujours présent dans l'URL. La résolution, sans profil utilisateur, est : préfixe d'URL, cookie `NEXT_LOCALE`, en-tête `Accept-Language`, puis `pt`. L'adresse `/` redirige vers la locale résolue. Le sélecteur navigue vers le même chemin avec un autre préfixe. Le cookie `NEXT_LOCALE` est posé par le middleware de `next-intl`.

Les trois fichiers de traduction sont rédigés dans leur langue. Les clés sont écrites avec leur fichier comme préfixe : `common.health.title` désigne la clé `health.title` de `common.json`, et `errors.X` désigne la clé `X` de `errors.json`. Clés affichées :

| Clé | Portugais | Français | Anglais |
|---|---|---|---|
| `common.appName` | Dados Imobiliários da Guiné-Bissau | Données immobilières de Guinée-Bissau | Guinea-Bissau property and land data |
| `common.health.title` | Estado da plataforma | État de la plateforme | Platform status |
| `common.loading` | A carregar… | Chargement… | Loading… |
| `common.error` | Não foi possível contactar a API. | Impossible de joindre l'API. | The API could not be reached. |
| `common.health.api` | API | API | API |
| `common.health.database` | Base de dados | Base de données | Database |
| `common.health.postgis` | PostGIS | PostGIS | PostGIS |
| `common.health.up` | operacional | opérationnelle | up |
| `common.health.down` | indisponível | indisponible | down |
| `errors.HEALTH_DATABASE_UNAVAILABLE` | A base de dados PostGIS não está acessível. | La base PostGIS est inaccessible. | The PostGIS database is unreachable. |
| `errors.INTERNAL_ERROR` | Ocorreu um erro interno. | Une erreur interne s'est produite. | An internal error occurred. |

Libellés du sélecteur : `Português`, `Français`, `English`.

Les valeurs `"up"` et `"down"` renvoyées par l'API sont traduites par `common.health.up` et `common.health.down`. Toute autre valeur est affichée telle quelle.

Affichage :

- Chargement : `common.loading`.
- HTTP 200 : titre, puis trois lignes API, base et PostGIS. La version PostGIS est le texte renvoyé par la base.
- HTTP 503 avec `message = HEALTH_DATABASE_UNAVAILABLE` : titre, phrase traduite de `errors.HEALTH_DATABASE_UNAVAILABLE`, puis les trois lignes avec l'API opérationnelle, la base indisponible et la version PostGIS affichée « — ».
- HTTP 500 avec `message = INTERNAL_ERROR` : phrase traduite de `errors.INTERNAL_ERROR`, sans lignes d'état.
- Tout autre cas (réseau, CORS, corps non JSON, code `message` inconnu) : `common.error`, sans lignes d'état. Aucune version inventée.

## Flux de santé

Succès, HTTP 200 :

```json
{
  "success": true,
  "data": { "api": "up", "database": "up", "postgis": "3.4 USE_GEOS=1 …" },
  "message": null,
  "meta": {}
}
```

Base indisponible, HTTP 503 :

```json
{
  "success": false,
  "data": { "api": "up", "database": "down", "postgis": null },
  "message": "HEALTH_DATABASE_UNAVAILABLE",
  "meta": {}
}
```

Erreur interne, HTTP 500 :

```json
{
  "success": false,
  "data": null,
  "message": "INTERNAL_ERROR",
  "meta": {}
}
```

## Erreurs

| Situation | HTTP | `message` | Effet visible |
|---|---|---|---|
| Flyway ou extension refusée | l'application ne démarre pas | — | pas de page saine |
| `DataAccessException` pendant la sonde | 503 | `HEALTH_DATABASE_UNAVAILABLE` | phrase traduite, base indisponible |
| Toute autre exception | 500 | `INTERNAL_ERROR` | phrase traduite de `errors.INTERNAL_ERROR` |
| API injoignable, CORS refusé ou corps non JSON | pas de réponse exploitable | — | `common.error` |

Le JSON d'erreur ne contient ni stack trace, ni SQL, ni identifiant de connexion. L'exception est journalisée côté serveur. `ApiExceptionHandler` est le seul endroit qui transforme une exception en `ApiResponse`. Pour `DataAccessException`, il construit `data` avec `api = "up"`, `database = "down"` et `postgis = null`. Pour toute autre exception, `data` vaut `null`.

## Tests

Backend, intégration, Testcontainers avec `postgis/postgis:16-3.4` (Docker requis) :

- Flyway s'applique.
- `postgis` et `pg_trgm` sont présents dans `pg_extension`.
- `GET /api/v1/health` répond 200, `success` vrai, `database` égal à `up`, `postgis` non vide.

Backend, unitaire, `@WebMvcTest` sur `HealthController` avec `ApiExceptionHandler` importé et la sonde simulée :

- Sonde en `DataAccessResourceFailureException` : HTTP 503, `success` faux, `message` égal à `HEALTH_DATABASE_UNAVAILABLE`, `data.database` égal à `down`, `data.postgis` nul.
- Sonde en `RuntimeException` : HTTP 500, `success` faux, `message` égal à `INTERNAL_ERROR`, `data` nul.
- Les réponses portent `Cache-Control: no-store`.

Frontend, Vitest et Testing Library, `HealthStatus` avec `fetch` simulé. Le rendu est enveloppé dans `NextIntlClientProvider` avec les messages `pt` :

- état de chargement ;
- succès : la version PostGIS reçue est affichée ;
- corps 503 : la phrase portugaise de `HEALTH_DATABASE_UNAVAILABLE` est affichée, la base est indisponible et la version est « — » ;
- corps 500 : la phrase portugaise de `INTERNAL_ERROR` est affichée ;
- échec réseau : `common.error` ;
- corps non JSON : `common.error`.

Frontend, autres tests Vitest :

- `LocaleSwitcher` : les trois liens pointent vers le même chemin avec le bon préfixe de locale.
- Cohérence des traductions : `pt`, `fr` et `en` ont exactement les mêmes clés dans `common.json` et dans `errors.json`.

Playwright n'est pas ajouté dans ce cycle. Les affichages `/fr` et `/en` ne sont donc vérifiés que manuellement.

## Contrôle manuel

Décrit dans le `README.md` à la racine :

- les trois URL `/pt`, `/fr`, `/en` affichent le titre dans la bonne langue ;
- avec Compose et le backend démarrés, la version PostGIS apparaît ;
- avec PostGIS arrêté, la page affiche la phrase d'indisponibilité en quelques secondes ;
- `curl http://localhost:3001/health` renvoie `OK` ;
- `curl http://localhost:3001/catalog` renvoie une liste de sources vide ;
- les journaux de `minio-init` montrent le bucket `documents`.

Le `README.md` note aussi les versions résolues de Spring Boot et de Next.js.

## Points à vérifier à la génération

- Le tag `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` existe sur le registre. Sinon, choisir le tag existant le plus proche et mettre le spec à jour.
- L'image `ghcr.io/maplibre/martin:1.16.1` existe et son healthcheck est exploitable.
- L'image MinIO fournit `curl` ou un autre outil pour le healthcheck.
- `docker compose up -d --wait` se comporte correctement avec `minio-init`.
- Les versions de `next-intl` 3.x, Tailwind 3 et shadcn/ui retenues sont compatibles avec Next.js 14.2.

## Fichiers ignorés

`.env`, `frontend/.env.local`, `backend/target/`, `frontend/node_modules/`, `frontend/.next/`.

## Changements de la révision 2

- Contradiction Git levée : `.gitignore` et `.dockerignore` créés sans dépôt.
- `errors.INTERNAL_ERROR` est maintenant affiché pour un 500.
- Affichage précisé pour le 503 (trois lignes) et pour les erreurs réseau ou non JSON.
- Lecture du corps JSON sur tout statut, `lib/api/health.ts`, `retry: false`.
- Java 21, Node 20 LTS, versions compatibles de `next-intl` et Tailwind, versions résolues notées dans le README.
- Délai de connexion Hikari à 3 s, CORS détaillé, `Cache-Control: no-store`.
- Ports Compose liés à `127.0.0.1`, `--wait`, noms des variables d'environnement.
- Fichiers `i18n/routing.ts` et `i18n/navigation.ts`, `request.ts` fusionnant les deux fichiers de messages, `<html lang>`.
- Tests ajoutés : chemin 500, parité des clés de traduction, sélecteur de langue, corps non JSON.
- Section « Points à vérifier à la génération », notes sur la fin de support des versions et sur les droits `CREATE EXTENSION`.
