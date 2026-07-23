# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone Spring Boot app for searching another application's log files. Configure a target
app's logs once (upload its `logback-spring.xml` or use the admin wizard), then search its live
and rotated/gzip log files — across multiple nodes (each with one or more named log outputs), by
date range, by a filter field (trace ID, session ID, ...), or by free text. Search is exposed both
as a server-rendered Thymeleaf/HTMX UI and as a public JSON REST API (including an SSE streaming
endpoint); only project configuration requires the single admin login.

Tech stack: Java 21 · Spring Boot 4.1 (MVC, Data JPA, Security, Session JDBC) · springdoc-openapi ·
Thymeleaf + HTMX + Alpine.js + Tailwind (CDN, no frontend build step) · Flyway · H2 (dev) /
PostgreSQL (prod) · Maven.

## Commands

```bash
mvnw.cmd spring-boot:run          # run locally (dev profile, in-memory H2, active by default)
mvnw.cmd clean package            # executable jar -> target/logutility-0.0.1-SNAPSHOT.jar
mvnw.cmd clean package -Pwar21    # traditional WAR -> target/logutility-java21.war (needs a JDK 21 toolchain entry in ~/.m2/toolchains.xml)
mvnw.cmd test                     # full test suite
mvnw.cmd test -Dtest=SearchServiceIntegrationTest        # single test class
mvnw.cmd test -Dtest=SearchServiceIntegrationTest#methodName  # single test method
```

App runs at http://localhost:8080. Dev admin login is `admin`/`admin` (set in
`application-dev.yml`, dev-profile only). `/h2-console` browses the in-memory DB;
`/actuator/health` is the liveness check; `/swagger-ui.html` and `/v3/api-docs` are the
auto-generated REST API docs (springdoc).

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
| `config.{security,search,openapi}` | Spring `@Configuration` classes (security filter chains, search tuning beans, OpenAPI metadata) |
| `controller.{web,project,search,common}` | Thymeleaf/HTMX controllers and `@RestController`s; `common` holds the shared REST error advice |
| `entity.project` | JPA entities (the only domain with entities) |
| `repository.project` | Spring Data repositories |
| `request.{project,parser,search}` | Inbound shapes: wizard `*Form` objects (session-held, mutable) and REST `*Request` records |
| `response.{project,parser,search,validation,common}` | Outbound shapes: REST `*Response`/view records and the wizard's read models |
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

### Two front doors, two security filter chains

`SecurityConfig` defines two independent `SecurityFilterChain`s, ordered by `securityMatcher`:

- **`apiSecurityFilterChain`** (`/api/**`) — stateless HTTP Basic, no session, no CSRF.
  `/api/search/**` is `permitAll`; `/api/projects/**` requires the single admin account on every
  request via an `Authorization` header (no cookie-based auth here at all).
- **`appSecurityFilterChain`** (everything else) — the server-rendered Thymeleaf/HTMX UI, form
  login, Spring Session JDBC. `/`, `/search/**`, `/login`, static assets, health, and the Swagger UI
  are public; `/admin/**` and `/config/**` require the admin session.

Both chains share one `UserDetailsService` (`AdminProperties`, BCrypt-encoded at startup, fails
fast if blank). CORS for `/api/**` is opt-in and off by default (`CorsConfig`, driven by
`loguty.api.cors.allowed-origins`) — no browser-based external UI can call the API cross-origin
until an admin explicitly configures its origin.

REST controllers, by domain:

| Controller | Path | Auth | Notes |
|---|---|---|---|
| `SearchApiController` | `POST /api/search`, `GET /api/search/projects[/{id}]` | public | JSON equivalent of the Thymeleaf search page |
| `SearchStreamController` | `GET /api/search/stream` (SSE), `GET /api/search/export` (plain-text download) | public | Both stream through `SearchServiceImpl.searchStreaming`; export writes directly to the servlet response instead of buffering |
| `ProjectApiController` | `/api/projects/**` | HTTP Basic (admin) | Full CRUD plus the wizard's helper actions (`logback/parse`, `sample-line/analyze`, `path-check`) as one-shot equivalents of the multi-step wizard; shares `ProjectWizardValidation` and `ProjectService.saveFromWizard` with it |

`ApiExceptionHandler` (`controller.common`, `@RestControllerAdvice(assignableTypes = {...})`)
scopes itself explicitly to those three REST controllers so the Thymeleaf/HTMX controllers keep
rendering their own error fragments untouched; it maps domain exceptions to a uniform `ApiError`
JSON body (404 for `ProjectNotFoundException`, 409 for duplicate-name `IllegalStateException`, 429
for `SearchOverloadedException`, etc.) — extend this list, not a new advice class, when adding
another REST controller.

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

Two consumption modes share steps 1–8: `search()` (batch, paginated, used by the Thymeleaf UI and
`SearchApiController`) and `searchStreaming()` (chunked callbacks, used by `SearchStreamController`
for both the SSE `/stream` endpoint and the plain-text `/export` download). A process-wide
`Semaphore` (`search.max-concurrent-searches`) caps how many searches run at once across all users
— since search has no login — and rejects extras with `SearchOverloadedException` → HTTP 429
rather than queuing them.

Search tuning lives in `SearchProperties`/`application.yml` (`search.*`): `max-results`,
`max-nodes-parallel`, `max-continuation-lines`, `max-date-range-days`, `max-concurrent-searches`,
`chunk-size` (SSE event batch size / producer buffer size), `sse-timeout-millis`.

### Admin wizard (`controller.project.ProjectWizardController`)

An HTMX multi-step flow (details → nodes → sample-line → fields → review) around one
`ProjectWizardForm` draft held in the `HttpSession` under key `"projectWizardDraft"`. Nodes are now
nested: each `NodeForm` holds a `List<LogFileForm>`, with separate add/remove endpoints at both the
node level (`/wizard/nodes/add|remove`) and the per-node log-output level
(`/wizard/nodes/{nodeIndex}/outputs/add|remove`). The "Test paths" button checks a `LogFile`'s live
and backup paths together and folds them into one combined status.

Every step handler mutates the draft then calls the private `step(...)` helper, which **re-sets**
the session attribute before rendering. This re-set is required, not decoration — see
[[spring-session-mutation-gotcha]] in memory: Spring Session JDBC serializes attributes and only
persists changes made through `setAttribute`, so in-place mutation of a session-held object is
silently lost without it. `MockMvc`-based tests can pass even when this is broken, since MockMvc
doesn't round-trip through the real session store — be skeptical of wizard test coverage for this
specific failure mode.

`ProjectApiController`'s create/update endpoints are a one-shot equivalent of this same flow: one
complete `ProjectRequest` payload instead of a multi-step session draft, validated with the same
`ProjectWizardValidation` and persisted with the same `ProjectService.saveFromWizard`.

### Security

Single in-memory admin user built from `AdminProperties` (`loguty.admin.username`/`.password`),
BCrypt-encoded at startup. See "Two front doors" above for the split between the stateless API
filter chain and the session-based UI filter chain. `DevH2ConsoleSecurityConfig` is a separate,
lower-priority filter chain that only applies to `/h2-console/**` and only matters in the dev
profile.

### Frontend conventions

- [`fragments/head.html`](src/main/resources/templates/fragments/head.html) is `th:replace`d into
  every page's `<head>`. It wires up Tailwind/HTMX/Alpine CDN scripts, the CSRF meta-tag +
  `htmx:configRequest` auto-attach, and four independent, purely client-side (localStorage-only,
  no cookie/server round trip) display preferences, each applied pre-paint via a synchronous IIFE
  to avoid a flash of the wrong state: **theme** (light/dark, `html.dark`), **density**
  (comfortable/compact, `html.compact`), **skin** (`default`/`console`/`signal`, `data-skin`
  attribute — Console is the default), and, Console-only, **font** (JetBrains Mono vs. system
  monospace, `data-console-font`).
- Each skin defines its own light + dark set of CSS custom properties (`--lvl-*`, `--tag-*`,
  `--banner-*`, plus skin-specific ones like `--console-*`/`--signal-*`); shared component rules
  (log-level pills, chips, banners, the stream/group-by views) are written once and colored
  entirely through those tokens, so adding a new skin means defining a token set, not duplicating
  every component rule.
- Active-project selection (admin project list's "Set active" button and the public search page's
  project switcher) is a shared cookie, `ProjectAdminController.ACTIVE_PROJECT_COOKIE`
  (`LOGUTY_ACTIVE_PROJECT`) — not tied to the login session, since searching is public.
  `SearchController` references the same constant directly.
- Public-facing Thymeleaf models use small `record` DTOs (`PublicProjectView`,
  `PublicFilterFieldView`, `ProjectSummaryDto`) — JPA entities are never passed to a template.

### Database

Flyway migrations live under `src/main/resources/db/migration/`: `common/` is the portable
baseline applied to every engine (now four migrations, including `V4__log_file.sql` for the
node → log-file split above); `{vendor}/` (currently `postgresql/`) is resolved automatically by
Flyway and holds vendor-specific overrides plus the Spring Session JDBC tables. In dev (H2, in
PostgreSQL-compatibility mode), Spring Session auto-initializes its own tables
(`initialize-schema: embedded`); in prod, Flyway owns them instead (`initialize-schema: never`,
via `V3__spring_session.sql`) — don't let both try to create the tables at once.

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

### Templates

Thymeleaf fragments live under `src/main/resources/templates/fragments/` and are composed with
`th:replace`/`th:insert`. Per [[thymeleaf-fragment-precedence-gotcha]]: never combine `th:replace`
with `th:switch`/`th:case`/`th:if`/`th:with` on the same tag — nest them on separate tags instead,
and when testing fragment rendering, assert the *absence* of sibling-case content, not just the
presence of the expected case.
