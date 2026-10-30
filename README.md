# Données immobilières et foncières — Guinée-Bissau

Cycle 0 : fondations locales (Next.js, Spring Boot, Docker Compose). Zone pilote : Bissau. Langue produit par défaut : portugais.

## Versions retenues

- Spring Boot 3.3.13
- Next.js 14.2.35
- Node 20
- Java 21

Spring Boot 3.3.13 et Next.js 14.2.35 ne sont plus couverts par le support open source de leurs éditeurs. La montée de version est hors de ce cycle.

## Profil `dev`

Le profil Spring `dev` est le profil local par défaut. Il ne sert qu'au poste de développement et doit disparaître avant un déploiement.

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
