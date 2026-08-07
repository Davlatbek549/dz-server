# dz-server

Backend for the [DZ](../DZ) Kotlin Multiplatform book app. Self-hosted: DZ owns its own user
accounts and user data, with no third-party backend-as-a-service in between.

## Stack

- Kotlin 2.3.21 on Java 21
- Spring Boot 4.1.0 — Web MVC, Security, Data JPA, Validation, Actuator
- PostgreSQL 17, schema managed by Flyway
- Gradle (Kotlin DSL)

> Spring Boot 4 renamed several starters (`spring-boot-starter-webmvc`, not `-web`) and moved to
> Jackson 3 under the `tools.jackson` group. Dependency snippets from Boot 3 tutorials will not
> resolve as written.

## Running locally

Start Postgres:

```bash
docker compose up -d
```

Then run the server:

```bash
./gradlew bootRun
```

It listens on <http://localhost:8080>.

| Endpoint | Auth | Purpose |
| --- | --- | --- |
| `GET /api/v1/ping` | public | Liveness smoke test — routing, security chain, JSON |
| `GET /actuator/health` | public | Health, including database connectivity |
| everything else | required | 401 until authenticated |

The database must be running before the server starts — Spring Data JPA fails fast without a
reachable datasource.

## Configuration

[`application.yaml`](src/main/resources/application.yaml) reads from environment variables and
falls back to local development defaults:

| Variable | Default |
| --- | --- |
| `DZ_DB_URL` | `jdbc:postgresql://localhost:5432/dz` |
| `DZ_DB_USER` | `dz` |
| `DZ_DB_PASSWORD` | `dz_local_password` |
| `DZ_SERVER_PORT` | `8080` |

The defaults are for local development only. Production supplies real values through the
environment; `.env` files are git-ignored.

## Database schema

Flyway owns the schema. Hibernate runs with `ddl-auto: validate`, so it verifies that entities
match the tables but never alters them.

Migrations live in [`src/main/resources/db/migration`](src/main/resources/db/migration) and are
named `V<n>__<description>.sql`. To change the schema, add a new migration — never edit one that
has already run.

## Tests

```bash
./gradlew build
```

`DzServerApplicationTests.contextLoads` boots the full application context, so it currently needs
Postgres running. Moving it onto Testcontainers would remove that dependency.

## Roadmap

Built in milestones, each complete before the next starts:

- **M0 — skeleton** *(done)*: boots, connects to Postgres, Flyway wired, health endpoints
- **M1 — auth**: `User` entity, BCrypt, JWT access + refresh, `/api/v1/auth/register|login|refresh|logout`
- **M2+**: users → books → shelves → reading progress → notes → highlights → reviews → friends →
  groups → notifications → search → recommendations

Endpoints are shaped to match the app's existing `AuthApi` contract so the client swaps backends
with minimal change.
