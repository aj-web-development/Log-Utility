# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone Spring Boot app for searching another application's log files. Configure a target
app's logs once (upload its `logback-spring.xml` or use the admin wizard), then search its live
and rotated/gzip log files — across multiple nodes (each with one or more named log outputs), by
date range, by a filter field (trace ID, session ID, ...), or by free text. The UI is a React SPA
(`frontend/`) that talks entirely to a public JSON REST API (including an SSE streaming endpoint);
only project configuration requires the single admin login, sent as HTTP Basic.

Tech stack: Java 21 · Spring Boot 4.1 (MVC, Data JPA, Security) · springdoc-openapi · Flyway · H2
(dev) / PostgreSQL (prod) · Maven, for the backend. React 19 · TanStack Router/Query · shadcn/Radix
· Tailwind v4 · Vite, for the frontend (`frontend/`) — built by Maven
(`frontend-maven-plugin`) straight into `src/main/resources/static`, so the backend still ships as
one self-contained jar/war with no separate frontend deploy step, even though building it now
requires Node (auto-downloaded by the Maven build, not required on `PATH`).

## Commands

```bash
mvnw.cmd spring-boot:run          # run the backend (dev profile, in-memory H2, active by default)
mvnw.cmd clean package            # builds frontend/ (npm ci && npm run build) THEN the executable jar -> target/logutility-0.0.1-SNAPSHOT.jar
mvnw.cmd clean package -Pwar21    # traditional WAR -> target/logutility-java21.war (needs a JDK 21 toolchain entry in ~/.m2/toolchains.xml)
mvnw.cmd test                     # full test suite (also runs the frontend build first, see generate-resources binding in pom.xml)
mvnw.cmd test -Dtest=SearchServiceIntegrationTest        # single test class
mvnw.cmd test -Dtest=SearchServiceIntegrationTest#methodName  # single test method
```

For frontend-only iteration, `cd frontend && npm run dev` runs a Vite dev server (default port
5173) that proxies `/api/**` to `http://localhost:8080` (see `frontend/vite.config.ts`) — run it
alongside `mvnw spring-boot:run` instead of rebuilding the whole jar on every change.

App runs at http://localhost:8080. Dev admin login is `admin`/`admin` (set in
`application-dev.yml`, dev-profile only) — the React login page checks it by sending it as HTTP
Basic against `/api/projects` and storing the header in `sessionStorage` on success (see
[[spa-basic-auth-shortcut]] in memory). `/h2-console` browses the in-memory DB; `/actuator/health`
is the liveness check; `/swagger-ui.html` and `/v3/api-docs` are the auto-generated REST API docs
(springdoc).

Production: set `SPRING_PROFILES_ACTIVE=prod` and supply `JDBC_DATABASE_URL` /
`JDBC_DATABASE_USERNAME` / `JDBC_DATABASE_PASSWORD` plus **`LOGUTY_ADMIN_USERNAME`** /
**`LOGUTY_ADMIN_PASSWORD`** — the app fails fast at startup if the admin credentials are missing
(`SecurityConfig#userDetailsService`).

Most tests are fast unit tests with no Spring context (search pipeline, Logback XML parser,
sample-line analyzer). A few (`*IntegrationTest`) boot the full app against the H2 dev profile.

## Architecture

For the full backend + frontend flow in detail — every request path, the complete search pipeline,
every wizard step, all frontend client-side behavior, config properties, and known gotchas — see
[docs/PROJECT_REFERENCE.md](docs/PROJECT_REFERENCE.md). It is a living document: **whenever a
change alters something it describes, update the relevant section there in the same piece of
work**, the same way you'd update this file. What follows here is the condensed version.

### Package layout: layer first, then domain

Unlike a typical package-by-feature layout, `com.app.logutility` is organized **by technical layer,
then by domain** — e.g. `controller.project`, `entity.project`, `service.project`, and
`repository.project` are four separate top-level packages, not one `project` package. Don't assume
"the project stuff" lives in one folder; the domain name is the second path segment, not the first.

| Top-level package | Contains |
|---|---|
| `config.{security,search,openapi}` | Spring `@Configuration` classes (the security filter chain, search tuning beans, OpenAPI metadata) |
| `controller.{web,project,search,common}` | `web` holds `SpaController` (forwards client-side SPA routes to `index.html`); `project`/`search` hold the `@RestController`s; `common` holds the shared REST error advice |
| `entity.project` | JPA entities (the only domain with entities) |
| `repository.project` | Spring Data repositories |
| `request.{project,parser,search}` | Inbound shapes: wizard `*Form` objects (reused by `ProjectApiController`, see below) and REST `*Request` records |
| `response.{project,parser,search,validation,common}` | Outbound shapes: REST `*Response`/view records and the wizard validation's read models |
| `dto.search` | Internal-only DTOs not exposed over HTTP (e.g. `ScanPlan`) |
| `exception.{project,parser,search}` | Domain exceptions, mapped to HTTP statuses by `ApiExceptionHandler` |
| `service.{project,parser,search,validation}` | Business logic — this is where the interesting code lives |

Layering is strict: controllers → services → repositories → entities. Controllers hold no business
logic; services use constructor injection only.

### Domain model

Four entities under `entity.project`, all with `UUID` ids stored as `VARCHAR(36)` (never assume a
native UUID column type — see [dual-JDK/portability note](#database) below):

```
Project (name UNIQUE, description)
 ├─ 1:N LogSource   (nodeLabel — just a node identity now)
 │       └─ 1:N LogFile (fileLabel, liveLogPath, backupRootPath, backupPathPattern,
 │                       lastCheckedAt/lastCheckStatus/lastCheckMessage)
 ├─ 1:N FilterField (key, label, mdcKey, matchType [EXACT_TOKEN|SUBSTRING|REGEX], linePrefix)
 └─ 1:1 LinePattern (timestampPattern, timestampRegexOrPosition, levelPattern, loggerPattern)
```

A node can write more than one distinct log output (`app.log`, `error.log`, `access.log`, ...);
each is its own `LogFile` row with its own live path, backup root, backup pattern, and cached
path-check result — `LogSource` itself carries none of that anymore (split out of `LogSource` by
migration `V4__log_file.sql`, which carries each pre-existing node's single config forward as its
first `LogFile`, labeled `"Application"`). The search pipeline and the wizard both operate at the
`LogFile` granularity, not the node granularity.

`backupPathPattern` uses this app's own placeholder syntax — `{date}` (always `yyyy-MM-dd`),
`{HH}`, `{i}`. `LogbackXmlParser` converts Logback's `%d{...}`/`%i` syntax into this one on upload.

### One stateless security chain

`SecurityConfig` defines a single `SecurityFilterChain`, stateless throughout (no session, no
CSRF): `/api/projects/**` requires the single admin account via HTTP Basic on every request;
everything else — `/api/search/**`, the static React SPA, and its client-routed paths (forwarded
to `index.html` by `SpaController`) — is `permitAll`. There is no server-rendered page left to
session-gate: the SPA shell always loads, and its own client-side auth guard
(`frontend/src/routes/_shell.admin.tsx`'s `beforeLoad`) decides what to show based on whether
credentials are stored — real enforcement stays entirely at the `/api/projects/**` boundary, since
route guards in an SPA are UX, not security. `DevH2ConsoleSecurityConfig` is a separate,
higher-priority chain that only applies to `/h2-console/**` and only matters in the dev profile.

The one `UserDetailsService` (`AdminProperties`, BCrypt-encoded at startup, fails fast if blank) is
also what the frontend's login page checks against directly (see [[spa-basic-auth-shortcut]]).
CORS for `/api/**` is opt-in and off by default (`CorsConfig`, driven by
`loguty.api.cors.allowed-origins`) — irrelevant to the bundled SPA (same-origin), but still there
for any other external API consumer.

REST controllers, by domain:

| Controller | Path | Auth | Notes |
|---|---|---|---|
| `SearchApiController` | `POST /api/search`, `GET /api/search/projects[/{id}]` | public | Backs the React search page |
| `SearchStreamController` | `GET /api/search/stream` (SSE), `GET /api/search/export` (plain-text download) | public | Both stream through `SearchServiceImpl.searchStreaming`; export writes directly to the servlet response instead of buffering |
| `ProjectApiController` | `/api/projects/**` | HTTP Basic (admin) | Full CRUD plus the wizard's helper actions (`logback/parse`, `sample-line/analyze`, `path-check`); backs the React admin wizard end to end |

`ApiExceptionHandler` (`controller.common`, `@RestControllerAdvice(assignableTypes = {...})`)
scopes itself explicitly to those three REST controllers (not `SpaController`, which just forwards
to the SPA shell); it maps domain exceptions to a uniform `ApiError` JSON body (404 for
`ProjectNotFoundException`, 409 for duplicate-name `IllegalStateException`, 429 for
`SearchOverloadedException`, etc.) — extend this list, not a new advice class, when adding another
REST controller.

### The search engine pipeline (`service.search` — the architectural core)

One `SearchService.search(...)` / `.searchStreaming(...)` call runs this pipeline, fanned out one
virtual thread **per (`LogSource`, `LogFile`) pair** — not just per node:

1. **`ProjectSearchLoader`** — a separate `@Transactional(readOnly=true)` bean that loads the
   project's nodes/log files/fields/line-pattern fully before the async fan-out starts.
2. **`DatePruner`** / **`GlobFileResolver`** — per `LogFile`: expand `{date}/{HH}/{i}` into globs
   from that file's own `backupRootPath`/`backupPathPattern`, then resolve to concrete paths.
3. **`LogSourceReaderFactory`** picks the plain or gzip `LogSourceReader` by extension; both stream
   lines lazily.
4. **`LogEntryAssembler`** (new) — groups raw lines into log *entries*: a line where the project's
   `LogLineParser` detects a timestamp starts a new entry, and every following line without one is
   folded in as a continuation (a stack trace, a wrapped message, pretty-printed JSON). This is
   driven entirely by the project's own line pattern, not a hardcoded heuristic, and is capped at
   `search.max-continuation-lines` so a project with a non-matching line pattern can't buffer a
   whole file into one unbounded entry.
5. Fast-reject by timestamp range, then the per-field `FieldMatcher` predicate
   (`ExactTokenMatcher`/`SubstringMatcher`/`RegexMatcher`, selected by `MatchType`).
6. **`NodeProducer`** — each scan unit's matches go into a small bounded blocking queue
   (backpressure: the scanning thread parks when it's full, so memory stays bounded regardless of
   total match count).
7. **`StreamingResultMerger`** — a bounded k-way merge (one buffered item per producer, via a
   priority queue) that emits a globally timestamp-ordered stream without ever materializing the
   whole result set. This replaced a flat "collect everything, then sort" merger specifically to
   support the streaming/SSE path below.
8. A hard cap (`search.max-results`) truncates and cancels remaining producer tasks rather than
   failing the search; unreachable nodes/files are recorded and skipped, never fail the whole search.

Two consumption modes share steps 1–8: `search()` (batch, paginated, used by `SearchApiController`
for the React search page's default view) and `searchStreaming()` (chunked callbacks, used by `SearchStreamController`
for both the SSE `/stream` endpoint and the plain-text `/export` download). A process-wide
`Semaphore` (`search.max-concurrent-searches`) caps how many searches run at once across all users
— since search has no login — and rejects extras with `SearchOverloadedException` → HTTP 429
rather than queuing them.

Search tuning lives in `SearchProperties`/`application.yml` (`search.*`): `max-results`,
`max-nodes-parallel`, `max-continuation-lines`, `max-date-range-days`, `max-concurrent-searches`,
`chunk-size` (SSE event batch size / producer buffer size), `sse-timeout-millis`.

### Admin wizard

Two front ends share one validation/persistence path: `ProjectWizardValidation` (still keyed off
`ProjectWizardForm`/`NodeForm`/`LogFileForm`/`FilterFieldForm` in `request.project`) and
`ProjectService.saveFromWizard`. `ProjectApiController`'s create/update endpoints
(`POST`/`PUT /api/projects`) are the only caller now — one complete `ProjectRequest` payload in,
validated and persisted the same way a multi-step draft used to be.

The actual step-by-step wizard UI lives entirely client-side now:
`frontend/src/features/wizard/ProjectWizard.tsx` renders 6 steps (details → nodes → sample line →
line pattern → fields → review) driven by local React state
(`frontend/src/features/wizard/types.ts`'s `WizardState`, converted to/from `ProjectRequest` /
`ProjectDetailResponse` at the edges) and only touches the backend for the per-step helper calls
(`POST /api/projects/sample-line/analyze`, `POST /api/projects/path-check`) plus the final
create/update. Nodes are nested exactly like the backend model: each node holds a list of log
files, added/removed independently at both levels. There is no more session draft, so
[[spring-session-mutation-gotcha]] no longer applies to this flow — worth knowing if you see it
referenced in older commits/PRs.

### Frontend (`frontend/`)

A React 19 + TanStack Router (client-only, no SSR) + TanStack Query SPA, built with Vite,
components from shadcn/Radix, styled with Tailwind v4. File-based routes under
`frontend/src/routes/` (dot-separated segments = nesting, e.g. `_shell.admin.projects.new.tsx` →
`/admin/projects/new`); `_shell.tsx` wraps every route in `AppShell` (sidebar + `TopBar`),
`_shell.admin.tsx` is the one place the client-side "must be signed in" redirect is enforced for
every `/admin/*` route (see "One stateless security chain" above for why that's UX, not the real
security boundary).

- `src/lib/api.ts` is the single fetch wrapper: attaches the stored `Authorization: Basic` header
  only to `/api/projects/**` calls, throws a typed error from the backend's `ApiError` shape on any
  non-2xx response, and redirects to `/login` on a `401`.
- `src/lib/auth.tsx` (`AuthProvider`/`useAuth`) — see [[spa-basic-auth-shortcut]]: there's no JSON
  login endpoint; signing in *is* a successful `GET /api/projects` call with a freshly-built Basic
  header, which then gets stored in `sessionStorage` and reused on every subsequent admin call.
- `src/lib/activeProject.ts` — the SPA's own client-side "active project" (`localStorage`), since
  the public search API takes `projectId` explicitly on every call and has no cookie/session
  concept of one. Shared by the search page and the `TopBar` project switcher.
- Three skins (**Signal** default, **Console**, **Paper**) plus light/dark and
  comfortable/compact density, all `localStorage`-only preferences applied pre-paint by the inline
  script in `frontend/index.html` to avoid a flash of the wrong state — same mechanism the old
  Thymeleaf `fragments/head.html` IIFE used. Design tokens live in `frontend/src/styles.css` as
  Tailwind v4 `@theme inline` custom properties (`--color-level-*`, `--color-sidebar-*`, ...); the
  `--level-*` tokens aren't overridden per-skin, so `bg-level-error` etc. read consistently across
  all three skins.
- Live/streaming search uses the native `EventSource` API directly (no SSE client library) against
  `GET /api/search/stream`; export is a plain `<a href="/api/search/export?...">` link (no
  fetch/blob code) since the backend already responds with a `Content-Disposition: attachment`.

### Database

Flyway migrations live under `src/main/resources/db/migration/`: `common/` is the portable
baseline applied to every engine (four migrations, including `V4__log_file.sql` for the node →
log-file split above); `{vendor}/` (currently `postgresql/`) is resolved automatically by Flyway
and holds vendor-specific overrides — there's no session-table migration to worry about anymore
now that the app is fully stateless (Spring Session JDBC was removed along with the Thymeleaf UI
that needed it); `h2/` holds `V5__dev_seed_project.sql`, a dev-only project seeded so a fresh
`mvnw spring-boot:run` has something to search immediately.

Adding a new target database (e.g. MySQL, SQL Server) means: add its JDBC driver + matching
`flyway-database-<db>` module to `pom.xml`; point `spring.datasource.*` at it; and, since raw
dependency jars don't self-configure under Spring Boot 4's modular auto-config (see
[[boot4-modular-autoconfig]] — this already bit Flyway once in this project), make sure any new
Spring Boot integration comes with its `spring-boot-*` auto-config module, not just the bare
library jar. SQL Server specifically needs a `db/migration/sqlserver/` override because its
`TIMESTAMP` type means "rowversion," not a date/time.

### Packaging

Default build produces an executable jar. The `war21` Maven profile switches packaging to a
traditional WAR compiled for Java 21 with Tomcat marked `provided`, for deployment to an external
servlet container — see [[dual-jdk-war-and-portability]]. This project targets **Java 21+ only**.

