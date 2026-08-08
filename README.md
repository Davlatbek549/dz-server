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
| `GET /api/v1/users/me` | bearer | The caller's profile |
| `PUT /api/v1/users/me` | bearer | Replace the caller's editable profile |
| `GET /api/v1/library/books` | bearer | The caller's library, newest first |
| `GET /api/v1/library/books/continue-reading` | bearer | Most recent part-read book, or `204` |
| `GET /api/v1/library/books/{bookId}` | bearer | One book from the caller's library |
| `PUT /api/v1/library/books/{bookId}` | bearer | Add the book, or refresh its stored snapshot |
| `PATCH /api/v1/library/books/{bookId}` | bearer | Update reading progress and/or favourite |
| `DELETE /api/v1/library/books/{bookId}` | bearer | Remove the book from the library |
| `GET /api/v1/collections` | bearer | The caller's collections, with their books |
| `GET /api/v1/collections/{collectionId}` | bearer | One collection |
| `PUT /api/v1/collections/{collectionId}` | bearer | Create or replace it, membership included |
| `DELETE /api/v1/collections/{collectionId}` | bearer | Delete it and its membership |
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

## User-scoped data

Every user-scoped route is `/me`, never `/users/{id}`. The caller's identity comes from the token,
so there is no path parameter to tamper with — a `{id}` route would need a check comparing path to
token on every single call, and the one place it was forgotten would expose everybody's data. New
modules should follow the same rule.

`PUT /users/me` replaces the editable profile, so a field omitted from the body is cleared. Email
and password are not part of it: changing an address needs a verification flow and changing a
password needs the old one, so both get their own endpoints later. Unknown fields in the body are
ignored rather than applied — there is a test asserting that `email`, `passwordHash` and `enabled`
cannot be smuggled in this way.

The counts on the profile (`booksRead`, `friendsCount`, `collectionsCount`) report `0` for now.
They belong to the library, friends and collections modules and will be derived from those tables
rather than stored on the user, so they cannot drift.

## There is no book catalogue

The server stores **each user's library**, not a catalogue of books.

Book metadata comes from **Gutendex and OpenLibrary** at runtime — the app's `RemoteBookRepository`
already fetches it, and `bookId` here is the id from that source. A catalogue table would be a
second copy of data we do not own, and would need syncing to stay correct.

So `library_books` holds a denormalised snapshot (title, author, cover, text URL) alongside the
user's own state (favourite, progress). Two users with the same book have two independent rows.
This mirrors the app's local `library_book` table, minus `is_downloaded` and `download_path`: those
describe one device's filesystem and stay local.

## Collections use ids the app chose

`PUT /collections/{collectionId}` takes an id the client generated, rather than a `POST` that
returns a server-assigned one. A local-first app has to be able to create a collection offline, so
it cannot wait for a round-trip — and because local and remote ids are identical, sync never has to
translate between them. Ids are unique per user, so two people can both own `sci-fi`.

A save replaces the collection wholesale, membership included, matching the app's local `update`,
which deletes every row and re-inserts the list it was given. Sending an empty `books` empties it.

> Worth knowing: the app derives the id from the title (`"Science Fiction"` → `science-fiction`), so
> two collections named the same collide and the second save overwrites the first. That is a client
> concern — the server behaves correctly either way — but it is worth fixing there.

## Deploying

Compute on **Render**, database on **Neon**. They are split because Render's free Postgres is
deleted after 30 days, while Neon's free tier is permanent. Neither needs a credit card.

The [Dockerfile](Dockerfile) builds the image and [render.yaml](render.yaml) describes the service,
so Render needs no settings entered by hand beyond the database details.

### One-time setup

1. **Neon** — create a project at <https://neon.com>, then take the connection details from the
   dashboard. The JDBC URL must end in `?sslmode=require`; Neon refuses plaintext connections.
2. **Render** — at <https://render.com>, create a Blueprint from this repository. It reads
   `render.yaml` and prompts for the three database variables.
3. Wait for the first build (several minutes — it compiles from source), then check
   `https://<your-service>.onrender.com/api/v1/ping`.

Flyway runs every migration against the empty Neon database on first startup, so there is no
schema step to do by hand.

### Environment

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` — set by `render.yaml` |
| `DZ_JWT_SECRET` | generated by Render; must be ≥ 32 bytes |
| `DZ_DB_URL` | `jdbc:postgresql://<neon-host>/<db>?sslmode=require` |
| `DZ_DB_USER`, `DZ_DB_PASSWORD` | from Neon |
| `PORT` | injected by the platform; do not set it |

The `prod` profile does three things that matter: it removes the development JWT secret so the app
**refuses to start** without a real one, hides the health endpoint's details (which otherwise
report disk paths and free space on a public URL), and stops logging SQL statements, which would
put user data in the platform's log viewer.

### What to expect

The free tier gives 512 MB. The container settles around **320 MB** under load, so there is
headroom, but the JVM flags in the Dockerfile are what make that true — without them the JVM sizes
its heap against the host and gets OOM-killed.

Render's free tier **spins down after 15 minutes of inactivity**, and the first request afterwards
takes 30–60 seconds while it starts again. Neon's compute also sleeps after 5 minutes and takes a
moment to wake. That is fine for development and demos; it is not suitable for real users, and the
app should show a loading state rather than appearing to hang.

### Pointing the app at it

Set `ApiConfig.baseUrl` in the DZ app to `https://<your-service>.onrender.com/api/v1`. Nothing else
changes — and because the URL is HTTPS, the cleartext exceptions on both platforms stop applying.

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
- **M2 — users** *(done)*: profile columns, `GET`/`PUT /api/v1/users/me`
- **M3 — library** *(done)*: each user's books with favourite and reading progress. Absorbs the
  "reading progress" milestone, because the app models progress on the library row rather than
  separately.
- **M4 — collections** *(done)*: user shelves and their membership, under client-chosen ids
- **M5+**: notes → highlights → reviews → friends → groups → notifications → search →
  recommendations

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
