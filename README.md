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

## Project structure

Package by feature, then by layer. Each feature module owns its whole vertical slice:

```text
com.example.dz.server
├── auth/          controller · service · repository · dto · mapper · entity · exception
├── users/         same layers
├── books/         (M2+) …
└── common/        cross-cutting config and error handling
```

Two rules keep it predictable:

- **The layer folders are the default**, even when one holds a single file. Uniformity across a
  dozen modules is worth more than saving a directory level in the small ones.
- **A named sub-package is allowed when files form a unit the layers don't describe.** `auth/jwt`
  is the current example: a filter, a `@Configuration` and a `@ConfigurationProperties` class have
  no home among the layers, and only make sense together.

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
| `POST /api/v1/auth/signup` | public | Create an account, returns a session |
| `POST /api/v1/auth/login` | public | Exchange credentials for a session |
| `POST /api/v1/auth/refresh` | public | Exchange a refresh token for a new session |
| `POST /api/v1/auth/logout` | bearer | Revoke the session (all sessions if no body) |
| `GET /api/v1/auth/me` | bearer | The authenticated user |
| `GET /api/v1/ping` | public | Liveness smoke test — routing, security chain, JSON |
| `GET /actuator/health` | public | Health, including database connectivity |
| everything else | bearer | 401 until authenticated |

The database must be running before the server starts — Spring Data JPA fails fast without a
reachable datasource.

## Authentication

Sessions use two tokens:

- an **access token** (JWT, 15 minutes) sent as `Authorization: Bearer <token>` on every request
- a **refresh token** (opaque, 30 days) exchanged at `/auth/refresh` for a new pair

Access tokens are stateless and cannot be withdrawn once signed, which is why they are short-lived.
Refresh tokens are rows in `refresh_tokens`, so logout can revoke them; only a SHA-256 hash is
stored. Each refresh **rotates** the token — using one invalidates it, so a stolen copy stops
working the next time the real client refreshes.

Passwords are hashed with BCrypt and never returned by any endpoint.

### Client contract

Paths and payloads match the app's existing `KtorAuthApi` and `AuthResponseDto`, so switching the
app over is a change to `ApiConfig.baseUrl` and nothing else. `AuthResponse` also carries
`refreshToken` and `expiresIn`, which the app currently ignores (`ignoreUnknownKeys = true`) and
can start reading whenever refresh support is wired up.

Errors come back as `{"code": "...", "message": "...", "fieldErrors": {...}}`, where `code` is one
of `InvalidCredentials`, `EmailAlreadyInUse`, `InvalidEmail`, `WeakPassword`, `UserDisabled`,
`TooManyAttempts`, `Unknown` — the exact names in the app's `AppError.AuthReason`.

> The client's `runRemote` currently maps any 401/403 to `AppError.Unauthorized` without reading
> the body, so these codes do not reach the UI yet. Surfacing them needs `KtorAuthApi` to parse the
> error body and throw `AuthBackendException`, as `FirebaseAuthApi` already does.

## Configuration

[`application.yaml`](src/main/resources/application.yaml) reads from environment variables and
falls back to local development defaults:

| Variable | Default |
| --- | --- |
| `DZ_DB_URL` | `jdbc:postgresql://localhost:5432/dz` |
| `DZ_DB_USER` | `dz` |
| `DZ_DB_PASSWORD` | `dz_local_password` |
| `DZ_SERVER_PORT` | `8080` |
| `DZ_JWT_SECRET` | a development placeholder |

`DZ_JWT_SECRET` signs every access token — anyone holding it can mint a token for any account. It
must be at least 32 bytes, and production must override the default.

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
- **M1 — auth** *(done)*: `User`, BCrypt, JWT access tokens, rotating refresh tokens, the four
  `/api/v1/auth/*` endpoints
- **M2+**: users → books → shelves → reading progress → notes → highlights → reviews → friends →
  groups → notifications → search → recommendations

Known gaps to pick up later:

- Login does no BCrypt comparison when the address is unknown, so response timing can still hint at
  which emails exist. Hashing against a dummy value on the miss path closes it.
- No rate limiting, so `TooManyAttempts` is defined but never returned. Brute-force protection is
  worth adding before real accounts exist.
- `emailVerified` is stored but never checked; there is no verification email yet.
- Revoked and expired rows in `refresh_tokens` are never cleaned up.
- No OpenAPI/Swagger yet — worth adding for the app developer once springdoc supports Boot 4.

Endpoints are shaped to match the app's existing `AuthApi` contract so the client swaps backends
with minimal change.
