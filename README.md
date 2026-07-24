# Log Utility

A standalone, multi-project log search tool. Configure a target application's logs once
(by uploading its `logback-spring.xml` or through a manual wizard), then search its live and
rotated/compressed logs — across multiple nodes, by date range, by an identifier field (trace
ID, session ID, ...), or by free text — with no login required to search. Only project
configuration is behind a single admin login.

## Features

- **Public search UI** — a React SPA: project switcher, dynamic filter form (built from the
  project's configured fields), date range + free text, batch or live/streaming results with
  color-coded log levels and an expandable full-line view, plus a plain-text export download. No
  account needed.
- **Admin project wizard** — a guided multi-step setup: project details, nodes (live + backup log
  paths, with a live "Test path" check), a sample-line analyzer that guesses the
  timestamp/level/logger format from a pasted line, and searchable filter fields.
- **Logback XML upload** — parses an uploaded `logback-spring.xml`: MDC keys (`%X{...}`) become
  suggested filter fields, and the rolling-file naming pattern becomes the backup path pattern.
- **Search engine** — scans live and gzip-rotated log files across all of a project's nodes
  concurrently (Java 21 virtual threads), pruned by date before touching the filesystem, with a
  hard result cap and a "some nodes were unreachable" warning rather than a failed search.
- **Single admin, no user database** — one admin account from environment variables, checked
  statelessly over HTTP Basic on every admin request; no sessions, nothing to share across app
  instances.
- **Database-portable schema** — runs on H2 (local dev) or PostgreSQL (production) via the same
  Flyway migrations; other SQL databases are supported by adding a driver and a small
  vendor-specific override (see [Database support](#database-support)).

## Tech stack

Backend: Java 21 · Spring Boot 4.1 (Spring MVC, Spring Data JPA, Spring Security) ·
springdoc-openapi · Flyway · H2 (dev) / PostgreSQL (prod) · Maven.
Frontend (`frontend/`): React 19 · TanStack Router/Query · shadcn/Radix · Tailwind v4 · Vite,
built by Maven into the same jar/war (see [Building](#building)).

## Running locally (dev profile / H2)

Prerequisites: JDK 21+. No database or other services to install — the dev profile uses an
in-memory H2 database and is active by default.

```bash
./mvnw spring-boot:run          # Linux/macOS
mvnw.cmd spring-boot:run        # Windows
```

The app starts on **http://localhost:8080**. Search is public immediately. To configure
projects, sign in at **http://localhost:8080/login** with the throwaway dev credentials
`admin` / `admin` (set in `application-dev.yml` — never used outside the dev profile).

Other useful local endpoints:

| Endpoint | Purpose |
|---|---|
| `/h2-console` | Browse the in-memory H2 database (dev profile only) |
| `/actuator/health` | Liveness/readiness check |

The H2 schema is created fresh on every restart (in-memory), and Flyway applies the same
migrations used in production, so schema issues surface locally too.

## Building

```bash
./mvnw clean package             # builds frontend/ (npm ci && npm run build), then the executable jar: target/logutility-0.0.1-SNAPSHOT.jar
./mvnw clean package -Pwar21     # traditional WAR for an external servlet container:
                                  #   target/logutility-java21.war
```

The frontend build runs automatically as part of `mvnw clean package` (via
`frontend-maven-plugin`, which downloads its own pinned Node — no need to have Node installed).
For frontend-only iteration, `cd frontend && npm run dev` runs a Vite dev server that proxies
`/api/**` to a `mvnw spring-boot:run` instance on port 8080.

Run the jar directly with `java -jar target/logutility-0.0.1-SNAPSHOT.jar`, or deploy the WAR
to any Java 21+ servlet container (Tomcat, etc.).

## Running tests

```bash
./mvnw test
```

Most tests are fast, isolated unit tests (no Spring context) for the search pipeline, the
Logback XML parser, and the sample-line analyzer. Integration tests boot the full app against
the H2 dev profile.

## Configuring for production

Activate the `prod` profile and supply the settings below via environment variables — nothing
production-specific needs to change in the packaged jar/WAR itself.

```bash
export SPRING_PROFILES_ACTIVE=prod
```

### Database (PostgreSQL)

| Variable | Default | Purpose |
|---|---|---|
| `JDBC_DATABASE_URL` | `jdbc:postgresql://localhost:5432/logutility` | JDBC connection URL |
| `JDBC_DATABASE_USERNAME` | `logutility` | Database user |
| `JDBC_DATABASE_PASSWORD` | *(empty)* | Database password |

Flyway runs automatically on startup and creates/updates the schema — no manual migration step.

### Admin credentials

| Variable | Required | Purpose |
|---|---|---|
| `LOGUTY_ADMIN_USERNAME` | yes | The single admin account's username |
| `LOGUTY_ADMIN_PASSWORD` | yes | The single admin account's password (hashed in memory at startup; never persisted to the database) |

The app **fails fast at startup** if either is missing or blank, so a production instance can
never come up without real admin credentials.

### Search tuning (optional)

| Property | Default | Purpose |
|---|---|---|
| `search.max-results` | `5000` | Hard cap on matched lines returned per search |
| `search.max-nodes-parallel` | `16` | Max nodes scanned concurrently per search |

Override via environment variables (`SEARCH_MAX_RESULTS`, `SEARCH_MAX_NODES_PARALLEL`) or a
mounted `application.yml`.

## Database support

Flyway migrations live under `src/main/resources/db/migration/`:

- **`common/`** — the portable baseline schema, applied to every database.
- **`{vendor}/`** — resolved automatically to the running engine (`postgresql`, `h2`, `mysql`,
  `oracle`, `sqlserver`, ...) for anything database-specific.

To run against a database other than PostgreSQL in production:
1. Add that database's JDBC driver and its `flyway-database-<db>` module to `pom.xml`.
2. Point `spring.datasource.*` at it (override `application-prod.yml` or set env vars).
3. Add `db/migration/<db>/Vn__*.sql` for anything vendor-specific.

One known gap: SQL Server's `TIMESTAMP` type means "rowversion", not a date/time — a SQL Server
target needs a `db/migration/sqlserver/` override using `DATETIME2` instead.

## Project layout

```
config       Cross-cutting Spring configuration
project      Project/LogSource/FilterField/LinePattern entities + repositories
project.config  Project CRUD service, wizard validation, and the REST controller both use
parser       Logback XML parser + sample-line analyzer
validation   Path-availability checking ("Test path")
search       The search engine pipeline (date pruning, file readers, matchers, fan-out, merge)
security     Single-admin authentication (stateless HTTP Basic on /api/projects/**)
web          SpaController - forwards the React SPA's client-side routes to index.html
```

The React SPA itself lives in `frontend/` (see [CLAUDE.md](CLAUDE.md) and
[docs/PROJECT_REFERENCE.md](docs/PROJECT_REFERENCE.md) for its structure).

Search is implemented as a pipeline of single-responsibility, independently-tested components
(date pruning → file resolution → streaming read → timestamp fast-reject → field matching →
merge), fanned out across a project's nodes on virtual threads and bounded by
`search.max-nodes-parallel`.
