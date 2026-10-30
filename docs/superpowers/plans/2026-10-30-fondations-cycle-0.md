# Fondations cycle 0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire démarrer PostGIS, Redis, MinIO et Martin dans Docker, et afficher sur une page Next.js localisée l'état de l'API et la version PostGIS.

**Architecture:** Compose publie les quatre services sur `127.0.0.1`. Spring Boot 3.3, lancé sur la machine, applique Flyway puis répond à `GET /api/v1/health`. Next.js 14, lancé sur la machine, appelle cette URL depuis le navigateur et traduit l'écran en portugais, français et anglais.

**Tech Stack:** Java 21, Spring Boot 3.3.13, Flyway, PostgreSQL 16 / PostGIS 3.4, Next.js 14.2.35, next-intl 3.26.5, Tailwind CSS 3.4.19, TanStack Query 5.105.0, Vitest 2.1.9.

## Global Constraints

- Aucun utilisateur, rôle, JWT, CI, JPA, SpringDoc, client Redis, client MinIO, tuile, page carte, fiche, admin ou Playwright.
- Pas de packages `domain` ni `application`.
- Java 21. Spring Boot parent `3.3.13`, version littérale. Dernier correctif 3.3 publié sur Maven Central.
- Node 20 LTS. Next.js `14.2.35`. `next-intl` `3.26.5`. Tailwind CSS `3.4.19`.
- Spring Boot 3.3 et Next.js 14 sont en fin de support open source. Le noter dans le README. Ne pas monter de version dans ce cycle.
- API unique : `GET /api/v1/health`. En-tête `Cache-Control: no-store` sur le succès et sur les erreurs.
- `message` JSON présent même quand il vaut `null`. `meta` est `{}`.
- Base indisponible : HTTP 503, `message` = `HEALTH_DATABASE_UNAVAILABLE`, `data.api` = `up`, `data.database` = `down`, `data.postgis` = `null`.
- Toute autre exception : HTTP 500, `message` = `INTERNAL_ERROR`, `data` = `null`.
- Le JSON d'erreur ne contient ni stack trace, ni SQL, ni identifiant de connexion. L'exception est journalisée côté serveur.
- `spring.datasource.hikari.connection-timeout` = `3000` dans `application-dev.yml`.
- CORS : `CorsFilter` à priorité maximale, origine `http://localhost:3000`, méthodes `GET` et `OPTIONS`, en-tête `Accept`, credentials interdits.
- Profil Spring par défaut : `dev`. Réservé au local.
- Ports Compose liés à `127.0.0.1`. Le port hôte de PostGIS est `5435` (conteneur `5432`). `5432`, `5433` et `5434` sont déjà pris. Pas de clé `version` dans Compose.
- Images : `postgis/postgis:16-3.4`, `redis:7.4-alpine`, `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`, `ghcr.io/maplibre/martin:1.16.1`.
- Identifiants locaux : PostgreSQL `immodata` / `immodata` / `immodata`, MinIO `minioadmin` / `minioadmin`.
- Ne pas exécuter `git config`. Si un commit échoue faute d'identité Git, s'arrêter et demander à l'utilisateur de la définir.

Les commandes partent de la racine du dépôt. Le shell est PowerShell. Les tests backend passent par `.\mvnw.cmd`.

---

## File map

- `docker-compose.yml` — PostGIS, Redis, MinIO, init du bucket, Martin.
- `.env.example` — variables Compose versionnées.
- `docker/minio-init.sh` — enchaîne les trois commandes `mc`.
- `docker/martin.yaml` — limite Martin au schéma `public`.
- `backend/` — API de santé, Flyway, tests.
- `frontend/` — page localisée qui affiche cette API.
- `.gitignore`, `.dockerignore` — secrets et artefacts.
- `README.md` — démarrage, versions, contrôle manuel.

---

### Task 1: Fichiers ignorés et dépôt local

**Files:**
- Create: `.gitignore`
- Create: `.dockerignore`

**Interfaces:**
- Consumes: rien.
- Produces: un dépôt Git local dont `.env` et `frontend/.env.local` sont ignorés.

- [ ] **Step 1: Écrire `.gitignore`**

```gitignore
.env
frontend/.env.local
backend/target/
frontend/node_modules/
frontend/.next/
```

- [ ] **Step 2: Écrire `.dockerignore`**

```dockerignore
.env
frontend/.env.local
backend/target/
frontend/node_modules/
frontend/.next/
.git
```

- [ ] **Step 3: Initialiser le dépôt**

Run: `git init`
Expected: `Initialized empty Git repository`

Run: `git check-ignore -v .env frontend/.env.local backend/target/ frontend/node_modules/ frontend/.next/`
Expected: une ligne par chemin, toutes pointant vers `.gitignore`.

- [ ] **Step 4: Commit**

```powershell
git add .gitignore .dockerignore
git commit -m "chore: ignore local env files and build outputs"
```

Expected: un commit créé.

---

### Task 2: Compose PostGIS, Redis, MinIO et Martin

**Files:**
- Create: `.env.example`
- Create: `docker/minio-init.sh`
- Create: `docker-compose.yml`
- Create: `docker/wait-result.txt`

**Interfaces:**
- Consumes: rien du backend.
- Produces: `http://127.0.0.1:5435` (Postgres `immodata`, conteneur toujours sur 5432), Redis `6379`, MinIO `9000`, bucket `documents`, Martin `3001` avec `/health` = `OK` et un catalogue vide.

- [ ] **Step 1: Écrire `.env.example`**

```text
POSTGRES_DB=immodata
POSTGRES_USER=immodata
POSTGRES_PASSWORD=immodata
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

- [ ] **Step 2: Écrire `docker/minio-init.sh`**

Le spec demande trois commandes `mc`. L'image n'a pas de mode script : un shell les enchaîne, et le seul outil appelé est `mc`.

```sh
#!/bin/sh
set -eu
mkdir -p "${MC_CONFIG_DIR}"
mc alias set local http://minio:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"
mc mb --ignore-existing local/documents
mc ls local/documents
```

- [ ] **Step 3: Écrire `docker-compose.yml`**

```yaml
name: immodata

services:
  postgis:
    image: postgis/postgis:16-3.4
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "127.0.0.1:5435:5432"
    volumes:
      - postgis-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s
    restart: unless-stopped

  redis:
    image: redis:7.4-alpine
    ports:
      - "127.0.0.1:6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 20
    restart: unless-stopped

  minio:
    image: pgsty/minio:RELEASE.2026-08-04T00-00-00Z
    command: ["server", "/data"]
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    ports:
      - "127.0.0.1:9000:9000"
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://127.0.0.1:9000/minio/health/live"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s
    restart: unless-stopped

  minio-init:
    image: pgsty/minio:RELEASE.2026-08-04T00-00-00Z
    entrypoint: ["/bin/sh", "/minio-init.sh"]
    environment:
      MC_CONFIG_DIR: /tmp/mc
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - ./docker/minio-init.sh:/minio-init.sh:ro
    depends_on:
      minio:
        condition: service_healthy
    restart: "no"

  martin:
    image: ghcr.io/maplibre/martin:1.16.1
    command: ["--config", "/config/martin.yaml"]
    ports:
      - "127.0.0.1:3001:3000"
    volumes:
      - ./docker/martin.yaml:/config/martin.yaml:ro
    depends_on:
      postgis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:3000/health"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s
    restart: unless-stopped

volumes:
  postgis-data:
  minio-data:
```

- [ ] **Step 4: Vérifier que les images existent**

```powershell
Copy-Item .env.example .env
docker manifest inspect pgsty/minio:RELEASE.2026-08-04T00-00-00Z
docker manifest inspect ghcr.io/maplibre/martin:1.16.1
docker manifest inspect postgis/postgis:16-3.4
docker manifest inspect redis:7.4-alpine
```

Expected: chaque commande se termine avec le code 0.

Si `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` est introuvable, arrêter cette tâche. Mettre à jour la ligne d'image dans la spec et dans ce plan avec un tag dont `docker manifest inspect` réussit, puis seulement ensuite continuer.

- [ ] **Step 5: Vérifier que MinIO contient curl**

Run: `docker run --rm --entrypoint curl pgsty/minio:RELEASE.2026-08-04T00-00-00Z --version`
Expected: une ligne de version curl, code 0. Cette image copie `curl` dans `/usr/bin`. Si la commande échoue, arrêter la tâche : le healthcheck MinIO du compose dépend de ce binaire.

- [ ] **Step 6: Démarrer la pile et trancher `--wait`**

```powershell
docker compose up -d --wait
```

Si le code de sortie est 0, `docker compose ps` montre `postgis`, `redis`, `minio` et `martin` healthy, et `minio-init` exited 0 :

```powershell
Set-Content -Path docker/wait-result.txt -Value "ok"
```

Si la commande échoue seulement parce que `minio-init` s'est déjà arrêté avec le code 0 :

```powershell
docker compose up -d --wait postgis redis minio martin
docker compose up minio-init
Set-Content -Path docker/wait-result.txt -Value "fallback"
```

Expected dans les deux cas : `minio-init` a affiché `documents` dans ses journaux.

```powershell
docker compose logs minio-init
curl.exe http://127.0.0.1:3001/health
curl.exe http://127.0.0.1:3001/catalog
```

Expected: les journaux contiennent `documents`. `/health` renvoie `OK`. `/catalog` renvoie HTTP 200 et une liste de sources vide.

- [ ] **Step 7: Commit**

Ne pas ajouter `.env`.

```powershell
git add .env.example docker-compose.yml docker/minio-init.sh docker/martin.yaml docker/wait-result.txt
git commit -m "build: run PostGIS, Redis, MinIO and Martin locally"
```

---

### Task 3: Contrat HTTP de santé, tests unitaires d'abord

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/immodata/gb/ImmoDataApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/immodata/gb/api/ApiResponse.java`
- Create: `backend/src/main/java/com/immodata/gb/api/health/HealthView.java`
- Create: `backend/src/main/java/com/immodata/gb/api/health/HealthController.java`
- Create: `backend/src/main/java/com/immodata/gb/api/error/ApiExceptionHandler.java`
- Create: `backend/src/main/java/com/immodata/gb/infrastructure/health/PostgisHealthProbe.java`
- Test: `backend/src/test/java/com/immodata/gb/api/health/HealthControllerTest.java`

**Interfaces:**
- Consumes: rien.
- Produces: `HealthController` `GET /api/v1/health`. `PostgisHealthProbe.probe()` retourne `HealthView`. `ApiResponse<T>(boolean success, T data, String message, Map<String, Object> meta)`. `HealthView(String api, String database, String postgis)`.

- [ ] **Step 1: Créer le projet Maven**

Écrire `backend/pom.xml` avec le parent `spring-boot-starter-parent` `3.3.13`, `java.version` `21`, et les dépendances `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `flyway-core`, `flyway-database-postgresql`, `postgresql` (runtime), `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`. Plugin `spring-boot-maven-plugin`. `groupId` `com.immodata`, `artifactId` `gb`, `version` `0.0.1-SNAPSHOT`.

Écrire `backend/src/main/java/com/immodata/gb/ImmoDataApplication.java` :

```java
package com.immodata.gb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ImmoDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImmoDataApplication.class, args);
    }
}
```

Écrire `backend/src/main/resources/application.yml` :

```yaml
spring:
  application:
    name: immodata-gb
  profiles:
    default: dev
  jackson:
    default-property-inclusion: always
```

Générer le wrapper depuis la racine :

```powershell
docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9.9-eclipse-temurin-21 mvn -N wrapper:wrapper
```

Expected: `backend/mvnw.cmd` existe.

- [ ] **Step 2: Écrire le test qui échoue**

`backend/src/test/java/com/immodata/gb/api/health/HealthControllerTest.java`

```java
package com.immodata.gb.api.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.immodata.gb.api.error.ApiExceptionHandler;
import com.immodata.gb.infrastructure.health.PostgisHealthProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import(ApiExceptionHandler.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostgisHealthProbe probe;

    @Test
    void databaseFailureReturns503() throws Exception {
        when(probe.probe()).thenThrow(new DataAccessResourceFailureException("down"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("HEALTH_DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.api").value("up"))
                .andExpect(jsonPath("$.data.database").value("down"))
                .andExpect(jsonPath("$.data.postgis").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meta").isMap());
    }

    @Test
    void unexpectedFailureReturns500() throws Exception {
        when(probe.probe()).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meta").isMap());
    }
}
```

- [ ] **Step 3: Lancer le test et constater l'échec**

```powershell
.\backend\mvnw.cmd -f backend\pom.xml -Dtest=HealthControllerTest test
```

Expected: échec de compilation, `HealthController` ou `PostgisHealthProbe` introuvable.

- [ ] **Step 4: Écrire le minimum pour faire passer le test**

`ApiResponse.java`

```java
package com.immodata.gb.api;

import java.util.Map;

public record ApiResponse<T>(boolean success, T data, String message, Map<String, Object> meta) {
}
```

`HealthView.java`

```java
package com.immodata.gb.api.health;

public record HealthView(String api, String database, String postgis) {
}
```

`PostgisHealthProbe.java` — le corps JDBC arrive à la tâche 4. Les tests de cette tâche mockent la classe.

```java
package com.immodata.gb.infrastructure.health;

import com.immodata.gb.api.health.HealthView;
import org.springframework.stereotype.Component;

@Component
public class PostgisHealthProbe {
    public HealthView probe() {
        throw new UnsupportedOperationException("JDBC probe is added with Flyway");
    }
}
```

`HealthController.java`

```java
package com.immodata.gb.api.health;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.infrastructure.health.PostgisHealthProbe;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final PostgisHealthProbe probe;

    public HealthController(PostgisHealthProbe probe) {
        this.probe = probe;
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<ApiResponse<HealthView>> health() {
        HealthView view = probe.probe();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, view, null, Map.of()));
    }
}
```

`ApiExceptionHandler.java`

```java
package com.immodata.gb.api.error;

import com.immodata.gb.api.ApiResponse;
import com.immodata.gb.api.health.HealthView;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<HealthView>> databaseUnavailable(DataAccessException exception) {
        log.warn("PostGIS health probe failed", exception);
        ApiResponse<HealthView> body = new ApiResponse<>(
                false,
                new HealthView("up", "down", null),
                "HEALTH_DATABASE_UNAVAILABLE",
                Map.of());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<HealthView>> internalError(Exception exception) {
        log.error("Unhandled API error", exception);
        ApiResponse<HealthView> body = new ApiResponse<>(false, null, "INTERNAL_ERROR", Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
```

- [ ] **Step 5: Relancer le test**

```powershell
.\backend\mvnw.cmd -f backend\pom.xml -Dtest=HealthControllerTest test
```

Expected: `Tests run: 2, Failures: 0`.

- [ ] **Step 6: Commit**

```powershell
git add backend
git commit -m "feat: map health failures to stable API error codes"
```

---

### Task 4: PostGIS, Flyway, CORS et test d'intégration

**Files:**
- Create: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/db/migration/V1__enable_spatial_extensions.sql`
- Create: `backend/src/main/java/com/immodata/gb/infrastructure/web/CorsConfig.java`
- Modify: `backend/src/main/java/com/immodata/gb/infrastructure/health/PostgisHealthProbe.java`
- Test: `backend/src/test/java/com/immodata/gb/infrastructure/health/PostgisHealthIT.java`

**Interfaces:**
- Consumes: `HealthView`, `ApiResponse`, `GET /api/v1/health`.
- Produces: `PostgisHealthProbe.probe()` exécute `SELECT postgis_version()` et retourne `new HealthView("up", "up", version)`. Extensions `postgis` et `pg_trgm` après migration.

- [ ] **Step 1: Écrire le test d'intégration qui échoue**

`PostgisHealthIT.java`

```java
package com.immodata.gb.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgisHealthIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("immodata")
            .withUsername("immodata")
            .withPassword("immodata")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flywayEnablesPostgisAndHealthReturnsVersion() throws Exception {
        List<String> extensions = jdbc.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('postgis', 'pg_trgm')",
                String.class);
        assertThat(extensions).containsExactlyInAnyOrder("postgis", "pg_trgm");

        ResponseEntity<String> response = rest.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("data").get("api").asText()).isEqualTo("up");
        assertThat(body.get("data").get("database").asText()).isEqualTo("up");
        assertThat(body.get("data").get("postgis").asText()).isNotBlank();
        assertThat(body.get("message").isNull()).isTrue();
        assertThat(body.get("meta").size()).isZero();
    }
}
```

- [ ] **Step 2: Lancer le test et constater l'échec**

```powershell
.\backend\mvnw.cmd -f backend\pom.xml -Dtest=PostgisHealthIT test
```

Expected: échec. La sonde lève `UnsupportedOperationException`, donc HTTP 500, ou Flyway n'a pas créé les extensions.

- [ ] **Step 3: Migration, configuration locale, sonde et CORS**

`V1__enable_spatial_extensions.sql`

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

`application-dev.yml`

```yaml
# Mêmes valeurs que la racine .env. Réservé à la machine de développement.
spring:
  datasource:
    url: jdbc:postgresql://localhost:5435/immodata
    username: immodata
    password: immodata
    hikari:
      connection-timeout: 3000

app:
  cors:
    allowed-origins: http://localhost:3000
```

Remplacer `PostgisHealthProbe` par :

```java
package com.immodata.gb.infrastructure.health;

import com.immodata.gb.api.health.HealthView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgisHealthProbe {
    private final JdbcTemplate jdbcTemplate;

    public PostgisHealthProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthView probe() {
        String version = jdbcTemplate.queryForObject("SELECT postgis_version()", String.class);
        return new HealthView("up", "up", version);
    }
}
```

`CorsConfig.java`

```java
package com.immodata.gb.infrastructure.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Accept"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
```

`@WebMvcTest` ne charge pas `app.cors.allowed-origins`. Le test unitaire importe seulement `ApiExceptionHandler`, pas `CorsConfig`. Ne pas l'importer.

- [ ] **Step 4: Relancer les deux tests**

```powershell
.\backend\mvnw.cmd -f backend\pom.xml verify
```

Expected: `HealthControllerTest` et `PostgisHealthIT` passent. `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```powershell
git add backend
git commit -m "feat: report the PostGIS version from the health endpoint"
```

---

### Task 5: Coque Next.js, messages et parité des clés

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/.nvmrc`
- Create: `frontend/.env.example`
- Create: `frontend/tsconfig.json`
- Create: `frontend/next.config.mjs`
- Create: `frontend/next-env.d.ts`
- Create: `frontend/postcss.config.js`
- Create: `frontend/tailwind.config.ts`
- Create: `frontend/app/globals.css`
- Create: `frontend/middleware.ts`
- Create: `frontend/i18n/routing.ts`
- Create: `frontend/i18n/navigation.ts`
- Create: `frontend/i18n/request.ts`
- Create: `frontend/messages/pt/common.json`
- Create: `frontend/messages/fr/common.json`
- Create: `frontend/messages/en/common.json`
- Create: `frontend/messages/pt/errors.json`
- Create: `frontend/messages/fr/errors.json`
- Create: `frontend/messages/en/errors.json`
- Create: `frontend/vitest.config.ts`
- Test: `frontend/messages/messages.test.ts`

**Interfaces:**
- Consumes: rien de l'API pour cette tâche.
- Produces: locales `pt`, `fr`, `en`, préfixe toujours présent, défaut `pt`. `getRequestConfig` fusionne `common` et `errors`. Les trois locales ont les mêmes clés.

- [ ] **Step 1: Écrire le test de parité qui échoue**

`frontend/messages/messages.test.ts`

```ts
import {expect, test} from 'vitest';
import enCommon from './en/common.json';
import frCommon from './fr/common.json';
import ptCommon from './pt/common.json';
import enErrors from './en/errors.json';
import frErrors from './fr/errors.json';
import ptErrors from './pt/errors.json';

function leafKeys(value: unknown, prefix = ''): string[] {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value).flatMap(([key, nested]) =>
      leafKeys(nested, prefix === '' ? key : `${prefix}.${key}`),
    );
  }
  return [prefix];
}

test('common locales share the same keys', () => {
  expect(leafKeys(frCommon).sort()).toEqual(leafKeys(ptCommon).sort());
  expect(leafKeys(enCommon).sort()).toEqual(leafKeys(ptCommon).sort());
});

test('error locales share the same keys', () => {
  expect(leafKeys(frErrors).sort()).toEqual(leafKeys(ptErrors).sort());
  expect(leafKeys(enErrors).sort()).toEqual(leafKeys(ptErrors).sort());
});
```

- [ ] **Step 2: Installer l'outillage et constater l'échec**

`frontend/package.json`

```json
{
  "name": "immodata-frontend",
  "private": true,
  "engines": {
    "node": ">=20 <21"
  },
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "test": "vitest run"
  },
  "dependencies": {
    "@tanstack/react-query": "5.105.0",
    "next": "14.2.35",
    "next-intl": "3.26.5",
    "react": "18.3.1",
    "react-dom": "18.3.1"
  },
  "devDependencies": {
    "@testing-library/react": "16.1.0",
    "@types/node": "20.17.30",
    "@types/react": "18.3.18",
    "@types/react-dom": "18.3.5",
    "@vitejs/plugin-react": "4.3.4",
    "autoprefixer": "10.4.21",
    "jsdom": "25.0.1",
    "postcss": "8.5.3",
    "tailwindcss": "3.4.19",
    "typescript": "5.7.3",
    "vitest": "2.1.9"
  }
}
```

`frontend/.nvmrc` contient exactement `20`.

`frontend/.env.example`

```text
NEXT_PUBLIC_API_URL=http://localhost:8080
```

`frontend/tsconfig.json` : `strict` true, `resolveJsonModule` true, `jsx` `preserve`, `moduleResolution` `bundler`, `paths` `{ "@/*": ["./*"] }`, plugin Next, include `next-env.d.ts` et `**/*.ts` / `**/*.tsx`.

`frontend/vitest.config.ts`

```ts
import path from 'node:path';
import react from '@vitejs/plugin-react';
import {defineConfig} from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname),
    },
  },
});
```

```powershell
cd frontend
npm install
npm test
```

Expected: échec, fichiers JSON introuvables.

- [ ] **Step 3: Écrire les messages et le routage i18n**

`messages/pt/common.json`

```json
{
  "appName": "Dados Imobiliários da Guiné-Bissau",
  "loading": "A carregar…",
  "error": "Não foi possível contactar a API.",
  "health": {
    "title": "Estado da plataforma",
    "api": "API",
    "database": "Base de dados",
    "postgis": "PostGIS",
    "up": "operacional",
    "down": "indisponível"
  }
}
```

`messages/fr/common.json`

```json
{
  "appName": "Données immobilières de Guinée-Bissau",
  "loading": "Chargement…",
  "error": "Impossible de joindre l'API.",
  "health": {
    "title": "État de la plateforme",
    "api": "API",
    "database": "Base de données",
    "postgis": "PostGIS",
    "up": "opérationnelle",
    "down": "indisponible"
  }
}
```

`messages/en/common.json`

```json
{
  "appName": "Guinea-Bissau property and land data",
  "loading": "Loading…",
  "error": "The API could not be reached.",
  "health": {
    "title": "Platform status",
    "api": "API",
    "database": "Database",
    "postgis": "PostGIS",
    "up": "up",
    "down": "down"
  }
}
```

`messages/pt/errors.json`

```json
{
  "HEALTH_DATABASE_UNAVAILABLE": "A base de dados PostGIS não está acessível.",
  "INTERNAL_ERROR": "Ocorreu um erro interno."
}
```

`messages/fr/errors.json`

```json
{
  "HEALTH_DATABASE_UNAVAILABLE": "La base PostGIS est inaccessible.",
  "INTERNAL_ERROR": "Une erreur interne s'est produite."
}
```

`messages/en/errors.json`

```json
{
  "HEALTH_DATABASE_UNAVAILABLE": "The PostGIS database is unreachable.",
  "INTERNAL_ERROR": "An internal error occurred."
}
```

`i18n/routing.ts`

```ts
import {defineRouting} from 'next-intl/routing';

export const routing = defineRouting({
  locales: ['pt', 'fr', 'en'],
  defaultLocale: 'pt',
  localePrefix: 'always',
});
```

`i18n/navigation.ts`

```ts
import {createNavigation} from 'next-intl/navigation';
import {routing} from './routing';

export const {Link, redirect, usePathname, useRouter} = createNavigation(routing);
```

`i18n/request.ts`

```ts
import {getRequestConfig} from 'next-intl/server';
import {routing} from './routing';

export default getRequestConfig(async ({requestLocale}) => {
  let locale = await requestLocale;
  if (!locale || !routing.locales.includes(locale as (typeof routing.locales)[number])) {
    locale = routing.defaultLocale;
  }
  const common = (await import(`../messages/${locale}/common.json`)).default;
  const errors = (await import(`../messages/${locale}/errors.json`)).default;
  return {
    locale,
    messages: {
      common,
      errors,
    },
  };
});
```

`middleware.ts`

```ts
import createMiddleware from 'next-intl/middleware';
import {routing} from './i18n/routing';

export default createMiddleware(routing);

export const config = {
  matcher: ['/((?!_next|.*\\..*).*)'],
};
```

`next.config.mjs`

```js
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./i18n/request.ts');

/** @type {import('next').NextConfig} */
const nextConfig = {};

export default withNextIntl(nextConfig);
```

`next-env.d.ts`

```ts
/// <reference types="next" />
/// <reference types="next/image-types/global" />
```

`postcss.config.js`

```js
module.exports = {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

`tailwind.config.ts`

```ts
import type {Config} from 'tailwindcss';

const config: Config = {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}'],
  theme: {
    extend: {},
  },
  plugins: [],
};

export default config;
```

`app/globals.css`

```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 4: Relancer le test**

```powershell
cd frontend
npm test
```

Expected: 2 tests passed.

- [ ] **Step 5: Commit**

Depuis la racine. Ne pas ajouter `frontend/.env.local`. `package-lock.json` est versionné.

```powershell
git add frontend
git commit -m "feat: add Portuguese, French and English message catalogs"
```

---

### Task 6: Carte d'état de l'API

**Files:**
- Create: `frontend/lib/utils.ts`
- Create: `frontend/lib/api/types.ts`
- Create: `frontend/lib/api/health.ts`
- Create: `frontend/components/ui/card.tsx`
- Create: `frontend/components/providers/QueryProvider.tsx`
- Create: `frontend/components/health/HealthStatus.tsx`
- Test: `frontend/components/health/HealthStatus.test.tsx`

**Interfaces:**
- Consumes: messages `common` et `errors`. `NEXT_PUBLIC_API_URL`.
- Produces: `fetchHealth(apiUrl: string): Promise<{status: number; body: ApiResponse<HealthView>}>`. Lève `Error('NETWORK')` ou `Error('NOT_JSON')`. `HealthStatus` affiche le chargement, le succès, le 503, le 500, le réseau et le corps non JSON.

- [ ] **Step 1: Écrire les tests du composant**

`frontend/components/health/HealthStatus.test.tsx`

```tsx
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, expect, test, vi} from 'vitest';
import ptCommon from '../../messages/pt/common.json';
import ptErrors from '../../messages/pt/errors.json';
import {HealthStatus} from './HealthStatus';

const messages = {common: ptCommon, errors: ptErrors};

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function renderStatus() {
  const client = new QueryClient({
    defaultOptions: {queries: {retry: false}},
  });
  return render(
    <NextIntlClientProvider locale="pt" messages={messages}>
      <QueryClientProvider client={client}>
        <HealthStatus />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

test('shows the loading text while the request is pending', () => {
  vi.stubGlobal('fetch', () => new Promise(() => {}));
  renderStatus();
  expect(screen.getByText('A carregar…')).toBeTruthy();
});

test('shows the PostGIS version returned by the API', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: true,
      data: {api: 'up', database: 'up', postgis: '3.4 USE_GEOS=1'},
      message: null,
      meta: {},
    }), {status: 200, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('3.4 USE_GEOS=1')).toBeTruthy();
  expect(screen.getByText('Estado da plataforma')).toBeTruthy();
  expect(screen.getAllByText('operacional').length).toBeGreaterThan(0);
});

test('shows an unknown status value as returned', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: true,
      data: {api: 'weird', database: 'up', postgis: '3.4'},
      message: null,
      meta: {},
    }), {status: 200, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('weird')).toBeTruthy();
});

test('shows the database-unavailable sentence and a dash', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: false,
      data: {api: 'up', database: 'down', postgis: null},
      message: 'HEALTH_DATABASE_UNAVAILABLE',
      meta: {},
    }), {status: 503, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('A base de dados PostGIS não está acessível.')).toBeTruthy();
  expect(screen.getByText('indisponível')).toBeTruthy();
  expect(screen.getByText('—')).toBeTruthy();
});

test('shows the internal error sentence', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({
      success: false,
      data: null,
      message: 'INTERNAL_ERROR',
      meta: {},
    }), {status: 500, headers: {'Content-Type': 'application/json'}})),
  );
  renderStatus();
  expect(await screen.findByText('Ocorreu um erro interno.')).toBeTruthy();
  expect(screen.queryByText('Estado da plataforma')).toBeNull();
});

test('shows the generic error when the network fails', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => {
    throw new Error('offline');
  }));
  renderStatus();
  expect(await screen.findByText('Não foi possível contactar a API.')).toBeTruthy();
});

test('shows the generic error when the body is not JSON', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response('not-json', {status: 200, headers: {'Content-Type': 'text/plain'}})),
  );
  renderStatus();
  expect(await screen.findByText('Não foi possível contactar a API.')).toBeTruthy();
});
```

- [ ] **Step 2: Lancer le test et constater l'échec**

```powershell
cd frontend
npm test
```

Expected: échec, `HealthStatus` introuvable. Le test de parité des messages reste vert.

- [ ] **Step 3: Implémenter le client et la carte**

`lib/api/types.ts`

```ts
export type HealthView = {
  api: string;
  database: string;
  postgis: string | null;
};

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string | null;
  meta: Record<string, unknown>;
};

export type HealthResult = {
  status: number;
  body: ApiResponse<HealthView | null>;
};
```

`lib/api/health.ts`

```ts
import type {ApiResponse, HealthResult, HealthView} from './types';

function isHealthBody(value: unknown): value is ApiResponse<HealthView | null> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const body = value as Partial<ApiResponse<HealthView | null>>;
  const messageOk = body.message === null || typeof body.message === 'string';
  const metaOk = body.meta !== null && typeof body.meta === 'object';
  return typeof body.success === 'boolean' && messageOk && metaOk && 'data' in body;
}

export async function fetchHealth(apiUrl: string): Promise<HealthResult> {
  let response: Response;
  try {
    response = await fetch(`${apiUrl}/api/v1/health`, {
      headers: {Accept: 'application/json'},
      cache: 'no-store',
    });
  } catch {
    throw new Error('NETWORK');
  }
  try {
    const body: unknown = await response.json();
    if (!isHealthBody(body)) {
      throw new Error('NOT_JSON');
    }
    return {status: response.status, body};
  } catch (error) {
    if (error instanceof Error && error.message === 'NOT_JSON') {
      throw error;
    }
    throw new Error('NOT_JSON');
  }
}
```

`lib/utils.ts`

```ts
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}
```

`components/ui/card.tsx`

```tsx
import type {HTMLAttributes} from 'react';
import {cn} from '@/lib/utils';

export function Card({className, ...props}: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-lg border border-slate-200 bg-white p-6 shadow-sm', className)} {...props} />;
}

export function CardHeader({className, ...props}: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('mb-4', className)} {...props} />;
}

export function CardTitle({className, ...props}: HTMLAttributes<HTMLHeadingElement>) {
  return <h2 className={cn('text-xl font-semibold', className)} {...props} />;
}

export function CardContent({className, ...props}: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('space-y-2', className)} {...props} />;
}
```

`components/providers/QueryProvider.tsx`

```tsx
'use client';

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {useState, type ReactNode} from 'react';

export function QueryProvider({children}: {children: ReactNode}) {
  const [client] = useState(
    () => new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          refetchOnWindowFocus: false,
        },
      },
    }),
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
```

`components/health/HealthStatus.tsx`

```tsx
'use client';

import {useQuery} from '@tanstack/react-query';
import {useTranslations} from 'next-intl';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {fetchHealth} from '@/lib/api/health';
import type {HealthView} from '@/lib/api/types';

export function HealthStatus() {
  const t = useTranslations('common');
  const errors = useTranslations('errors');
  const query = useQuery({
    queryKey: ['health'],
    queryFn: () => fetchHealth(process.env.NEXT_PUBLIC_API_URL ?? ''),
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (query.isPending) {
    return <p>{t('loading')}</p>;
  }
  if (query.isError || !query.data) {
    return <p>{t('error')}</p>;
  }

  const {status, body} = query.data;
  if (status === 500 && body.message === 'INTERNAL_ERROR') {
    return <p>{errors('INTERNAL_ERROR')}</p>;
  }
  if (body.message !== null && body.message !== 'HEALTH_DATABASE_UNAVAILABLE') {
    return <p>{t('error')}</p>;
  }

  const data: HealthView | null = body.data;
  if (!data) {
    return <p>{t('error')}</p>;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('health.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        {status === 503 && body.message === 'HEALTH_DATABASE_UNAVAILABLE' ? (
          <p>{errors('HEALTH_DATABASE_UNAVAILABLE')}</p>
        ) : null}
        <StatusLine label={t('health.api')} value={labelFor(t, data.api)} />
        <StatusLine label={t('health.database')} value={labelFor(t, data.database)} />
        <StatusLine
          label={t('health.postgis')}
          value={status === 503 ? '—' : (data.postgis ?? '—')}
        />
      </CardContent>
    </Card>
  );
}

function labelFor(t: ReturnType<typeof useTranslations<'common'>>, value: string): string {
  if (value === 'up') {
    return t('health.up');
  }
  if (value === 'down') {
    return t('health.down');
  }
  return value;
}

function StatusLine({label, value}: {label: string; value: string}) {
  return (
    <p>
      <span>{label}</span> <span>{value}</span>
    </p>
  );
}
```

`data` est annoté `HealthView | null`, donc l'import de ce type reste utilisé.

- [ ] **Step 4: Relancer les tests frontend**

```powershell
cd frontend
npm test
```

Expected: les 7 tests de `HealthStatus` et les 2 tests de messages passent.

- [ ] **Step 5: Commit**

```powershell
git add frontend
git commit -m "feat: show API and PostGIS status on the home card"
```

---

### Task 7: Sélecteur de langue et page

**Files:**
- Create: `frontend/components/locale/LocaleSwitcher.tsx`
- Create: `frontend/app/[locale]/layout.tsx`
- Create: `frontend/app/[locale]/page.tsx`
- Test: `frontend/components/locale/LocaleSwitcher.test.tsx`

**Interfaces:**
- Consumes: `Link` et `usePathname` de `@/i18n/navigation`, `HealthStatus`, `QueryProvider`.
- Produces: trois liens dont les `href` sont `/pt`, `/fr` et `/en` quand le chemin courant est `/`. `<html lang>` reprend la locale. L'en-tête affiche `common.appName`.

- [ ] **Step 1: Écrire le test du sélecteur**

`frontend/components/locale/LocaleSwitcher.test.tsx`

```tsx
import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {expect, test, vi} from 'vitest';
import ptCommon from '../../messages/pt/common.json';
import ptErrors from '../../messages/pt/errors.json';
import {LocaleSwitcher} from './LocaleSwitcher';

vi.mock('next/navigation', () => ({
  usePathname: () => '/pt',
  useRouter: () => ({push: vi.fn(), replace: vi.fn(), prefetch: vi.fn()}),
  useSearchParams: () => new URLSearchParams(),
}));

test('links keep the current path and switch the locale prefix', () => {
  render(
    <NextIntlClientProvider locale="pt" messages={{common: ptCommon, errors: ptErrors}}>
      <LocaleSwitcher />
    </NextIntlClientProvider>,
  );
  expect(screen.getByRole('link', {name: 'Português'}).getAttribute('href')).toBe('/pt');
  expect(screen.getByRole('link', {name: 'Français'}).getAttribute('href')).toBe('/fr');
  expect(screen.getByRole('link', {name: 'English'}).getAttribute('href')).toBe('/en');
});
```

- [ ] **Step 2: Lancer le test et constater l'échec**

```powershell
cd frontend
npm test
```

Expected: échec, `LocaleSwitcher` introuvable.

- [ ] **Step 3: Sélecteur, layout et page**

`components/locale/LocaleSwitcher.tsx`

```tsx
'use client';

import {Link, usePathname} from '@/i18n/navigation';
import {routing} from '@/i18n/routing';

const labels = {
  pt: 'Português',
  fr: 'Français',
  en: 'English',
} as const;

export function LocaleSwitcher() {
  const pathname = usePathname();
  return (
    <nav>
      {routing.locales.map((locale) => (
        <Link key={locale} href={pathname} locale={locale}>
          {labels[locale]}
        </Link>
      ))}
    </nav>
  );
}
```

`app/[locale]/layout.tsx`

```tsx
import type {ReactNode} from 'react';
import {NextIntlClientProvider} from 'next-intl';
import {getMessages, getTranslations, setRequestLocale} from 'next-intl/server';
import {notFound} from 'next/navigation';
import {QueryProvider} from '@/components/providers/QueryProvider';
import {LocaleSwitcher} from '@/components/locale/LocaleSwitcher';
import {routing} from '@/i18n/routing';
import '../globals.css';

export function generateStaticParams() {
  return routing.locales.map((locale) => ({locale}));
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: {locale: string};
}) {
  const {locale} = params;
  if (!routing.locales.includes(locale as (typeof routing.locales)[number])) {
    notFound();
  }
  setRequestLocale(locale);
  const messages = await getMessages();
  const t = await getTranslations('common');
  return (
    <html lang={locale}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <QueryProvider>
            <header>
              <p>{t('appName')}</p>
              <LocaleSwitcher />
            </header>
            {children}
          </QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
```

`app/[locale]/page.tsx`

```tsx
import {HealthStatus} from '@/components/health/HealthStatus';

export default function HomePage() {
  return (
    <main>
      <HealthStatus />
    </main>
  );
}
```

Aucun lien vers `/map` ou `/admin`.

- [ ] **Step 4: Relancer les tests**

```powershell
cd frontend
npm test
```

Expected: le test du sélecteur passe, ainsi que ceux de la carte et des messages.

Si les `href` rendus portent un slash final (`/pt/`), ajuster l'assertion du test pour la forme réelle produite par `next-intl` 3.26.5, à condition que le préfixe de locale et le chemin `/` restent exacts. Ne pas changer le composant pour satisfaire un slash inventé.

- [ ] **Step 5: Vérifier que la page se compile**

```powershell
cd frontend
Copy-Item .env.example .env.local
npm run build
```

Expected: compilation réussie. `.env.local` reste non versionné.

- [ ] **Step 6: Commit**

```powershell
git add frontend
git commit -m "feat: switch the shell between Portuguese, French and English"
```

---

### Task 8: README et critère de succès

**Files:**
- Create: `README.md`
- Modify: `docs/superpowers/specs/2026-10-30-fondations-cycle-0-design.md` seulement si un tag d'image a dû changer à la tâche 2.

**Interfaces:**
- Consumes: `docker/wait-result.txt` (`ok` ou `fallback`).
- Produces: le mode d'emploi du critère de succès de la spec.

- [ ] **Step 1: Écrire `README.md`**

Si `docker/wait-result.txt` vaut `ok`, la commande de démarrage est `docker compose up -d --wait`.

Si elle vaut `fallback`, les commandes sont :

```text
docker compose up -d --wait postgis redis minio martin
docker compose up minio-init
```

Le README contient, en français :

- la fin de support open source de Spring Boot 3.3.13 et Next.js 14.2.35, et le fait que la montée de version est hors de ce cycle ;
- les versions retenues : Spring Boot 3.3.13, Next.js 14.2.35, Node 20, Java 21 ;
- le profil `dev` est local et doit disparaître avant un déploiement ;
- `CREATE EXTENSION` fonctionne ici parce que l'utilisateur `immodata` est superuser de l'image PostGIS ;
- la séquence : copier `.env.example` vers `.env`, copier `frontend/.env.example` vers `frontend/.env.local`, démarrer Compose avec la commande retenue, `cd backend` puis `.\mvnw.cmd spring-boot:run`, `cd frontend` puis `npm run dev`, ouvrir `http://localhost:3000/pt` ;
- le contrôle manuel : `/pt`, `/fr` et `/en` affichent le nom de l'application dans la bonne langue ; la version PostGIS apparaît quand Compose et le backend tournent ; PostGIS arrêté, la phrase d'indisponibilité apparaît en quelques secondes ; `curl http://127.0.0.1:3001/health` renvoie `OK` ; `curl http://127.0.0.1:3001/catalog` renvoie une liste de sources vide ; les journaux de `minio-init` montrent `documents`.

- [ ] **Step 2: Contrôle manuel du critère de succès**

Avec Compose déjà sain depuis la tâche 2 :

```powershell
.\backend\mvnw.cmd -f backend\pom.xml verify
cd frontend
npm test
```

Expected: les deux commandes réussissent.

Démarrer le backend et le frontend dans deux terminaux. Ouvrir `http://localhost:3000/pt`. La page affiche `Dados Imobiliários da Guiné-Bissau`, `Estado da plataforma` et une version PostGIS non vide.

Ouvrir `/fr` et `/en`. Les titres sont respectivement `Données immobilières de Guinée-Bissau` et `Guinea-Bissau property and land data`.

```powershell
curl.exe http://127.0.0.1:3001/health
curl.exe http://127.0.0.1:3001/catalog
docker compose logs minio-init
```

Expected: `OK`, catalogue sans source, journal contenant `documents`.

- [ ] **Step 3: Commit**

```powershell
git add README.md
git commit -m "docs: describe how to start the cycle 0 stack"
```

Si la spec a été modifiée pour un tag d'image, l'ajouter à ce commit.
