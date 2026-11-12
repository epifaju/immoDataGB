# Données immobilières et foncières — Guinée-Bissau

Cycle 0 : fondations locales (Next.js, Spring Boot, Docker Compose).  
Bloc 1 : carte MapLibre + liste synchronisée + API spatiale PostGIS (seed Bissau). Zone pilote : Bissau. Langue produit par défaut : portugais.

## Versions retenues

- Spring Boot 3.3.13
- Next.js 14.2.35
- Node 20
- Java 21

Spring Boot 3.3.13 et Next.js 14.2.35 ne sont plus couverts par le support open source de leurs éditeurs. La montée de version est hors de ce cycle.

## Profil `dev`

Le profil Spring `dev` est le profil local par défaut. Il ne sert qu'au poste de développement et doit disparaître avant un déploiement.

## Secrets et dépôt Git

| Fichier | Suivi Git ? | Contenu |
|---|---|---|
| `.env`, `frontend/.env.local` | Non (`.gitignore`) | Copie locale depuis les `.example` |
| `.env.example`, `frontend/.env.example` | Oui | Placeholders locaux uniquement |
| `application-dev.yml` | Oui | Défauts locaux surchargeables via `SPRING_DATASOURCE_*` |
| `docker/martin.yaml` | Oui | `${POSTGRES_*}` injectés par Compose |

Identifiants `immodata` / `minioadmin` : **uniquement pour localhost**, ports liés à `127.0.0.1`. Ne pas les réutiliser en staging/prod. Pas de JWT, PEM ni clé cloud dans ce dépôt. Avant un push : vérifier `git status` (aucun `.env`) et `git check-ignore -v .env`.

## Extensions PostgreSQL

`CREATE EXTENSION` fonctionne ici parce que l'utilisateur `immodata` est superuser de l'image PostGIS.

## Démarrage

Prérequis : Docker, Java 21, Node 20.

Depuis la racine du dépôt :

1. Copier `.env.example` vers `.env`.
2. Copier `frontend/.env.example` vers `frontend/.env.local`.
3. Démarrer la pile :

```text
docker compose up -d --wait
```

4. Backend :

```text
cd backend
.\mvnw.cmd spring-boot:run
```

Spring Boot écoute sur le port par défaut 8080 et sert `GET /api/v1/health`. `frontend/.env.example` définit déjà `NEXT_PUBLIC_API_URL=http://localhost:8080`.

5. Frontend, dans un autre terminal :

```text
cd frontend
npm run dev
```

6. Ouvrir `http://localhost:3000/pt`.

## Contrôle manuel

Avec Compose et les deux applications démarrés :

- `http://localhost:3000/pt` affiche `Dados Imobiliários da Guiné-Bissau`, `Estado da plataforma` et une version PostGIS non vide.
- `http://localhost:3000/fr` affiche `Données immobilières de Guinée-Bissau`.
- `http://localhost:3000/en` affiche `Guinea-Bissau property and land data`.

La version PostGIS apparaît quand Compose et le backend tournent. PostGIS arrêté, la phrase d'indisponibilité apparaît en quelques secondes :

- portugais : `A base de dados PostGIS não está acessível.`
- français : `La base PostGIS est inaccessible.`
- anglais : `The PostGIS database is unreachable.`

Martin et MinIO :

```text
curl.exe http://127.0.0.1:3001/health
curl.exe http://127.0.0.1:3001/catalog
docker compose logs minio-init
```

`/health` renvoie `OK`. `/catalog` renvoie une liste de sources vide. Les journaux de `minio-init` montrent `documents`.

## Bloc 1 — Carte

Avec Compose, backend (profil `dev`) et frontend démarrés :

- Ouvrir `http://localhost:3000/pt/map` (lien « Mapa » dans l’en-tête).
- Page 0 : 20 marqueurs et « 1–20 de 24 » ; Suivant → « 21–24 de 24 ».
- Clic marqueur : popup (badge + summary). Clic liste : popup ; la carte ne se déplace que si le point est hors vue.
- Filtres / pan-zoom : URL mise à jour ; coller l’URL restaure l’état. Changer de langue conserve la query string.

```text
curl.exe "http://localhost:8080/api/v1/properties?bbox=-15.70,11.80,-15.50,11.95"
```

Attendu : `totalElements` 24, `totalPages` 2, 20 items (`BIS-024` … `BIS-005`).

Si les ports 3000 / 8080 sont pris, utiliser 3010 / 8081 et ajuster `NEXT_PUBLIC_API_URL` / CORS comme au cycle 0.

Le seed (`db/seed`) ne s’applique qu’en profil `dev`. Il est idempotent (biens mis à jour, sources de la plage UUID recréées) : un redémarrage du backend suffit en général, sans `docker compose down -v`.

## Bloc 2 — Fiche bien

- Depuis la carte : popup → « Ver ficha » / « Voir la fiche » → `/pt/properties/{id}` (conserve bbox/filtres/page).
- « Voltar ao mapa » restaure la vue ; fiche ouverte sans query → retour `/map`.
- Badges confiance + documentation, adresse si présente, liste des sources.

```text
curl.exe "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000001"
curl.exe "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000005/sources"
```

## Bloc 3 — Confiance

Le recalcul est protégé par le drapeau `app.confidence.recalculation-enabled` : `false` par défaut (`application.yml`, les deux `POST` répondent 404 `NOT_FOUND`), `true` avec le profil `dev`.

Avec le profil `dev` (drapeau à `true`) :

```text
curl.exe -X POST "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000003/confidence/recalculate"
curl.exe -X POST "http://localhost:8080/api/v1/organisations/00000000-0000-0000-0000-00000000000a/confidence/recalculate"
curl.exe "http://localhost:8080/api/v1/properties/00000000-0000-0000-0001-000000000003/sources"
```

Rafraîchir la fiche et la carte après le recalcul : le bien 3 affiche 30/100 avec le résumé généré `Pontuação 30/100 (baixa). Critérios: 1 unidade de fonte (+20); documentação parcial (+10).`, et trois biens (4, 14, 19) sont `high`. Ce résumé reste en portugais, quelle que soit la langue de l'interface. `GET …/sources` inclut `weight` ; la fiche l'affiche à côté de la pastille vérifiée.

Pour retrouver les scores d'origine du seed : `docker compose down -v`.
