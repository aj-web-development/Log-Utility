# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone Spring Boot app for searching another application's log files. Configure a target
app's logs once (upload its `logback-spring.xml` or use the admin wizard), then search its live
and rotated/gzip log files — across multiple nodes, by date range, by a filter field (trace ID,
session ID, ...), or by free text. Searching is public (no login); only project configuration
sits behind a single admin login.

Tech stack: Java 21 · Spring Boot 4.1 (MVC, Data JPA, Security, Session JDBC) · Thymeleaf + HTMX +
Alpine.js + Tailwind (CDN, no frontend build step) · Flyway · H2 (dev) / PostgreSQL (prod) · Maven.

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
`application-dev.yml`, dev-profile only). `/h2-console` browses the in-memory DB; `/actuator/health`
is the liveness check.

Production: set `SPRING_PROFILES_ACTIVE=prod` and supply `JDBC_DATABASE_URL` /
`JDBC_DATABASE_USERNAME` / `JDBC_DATABASE_PASSWORD` plus **`LOGUTY_ADMIN_USERNAME`** /
**`LOGUTY_ADMIN_PASSWORD`** — the app fails fast at startup if the admin credentials are missing
(`SecurityConfig#userDetailsService`).

Most tests are fast unit tests with no Spring context (search pipeline, Logback XML parser,
sample-line analyzer). A few (`*IntegrationTest`) boot the full app against the H2 dev profile.

## Architecture

Package layout under `com.app.logutility`:

| Package | Responsibility |
|---|---|
| `project` | `Project` / `LogSource` / `FilterField` / `LinePattern` JPA entities + repositories |
| `project.config` | Project CRUD (`ProjectService`), the admin setup wizard, and its controllers |
| `parser` | Logback XML parser + sample-line analyzer |
| `validation` | Path-availability checking ("Test path") |
| `search` | The search engine pipeline |
| `security` | Single-admin authentication (`SecurityConfig`) |
| `web` | Public search UI + login/admin controllers |

**Domain model**: `Project` is the aggregate root — one-to-many `LogSource` (a node/server with a
live log path + backup root path + backup naming pattern), one-to-many `FilterField` (a searchable
key, e.g. an MDC field, with a `MatchType`: exact token / substring / regex), and a one-to-one
`LinePattern` (how to parse a line: timestamp/level/logger position or regex). All entity IDs are
`UUID` stored as `VARCHAR(36)` (see [dual-JDK/portability note](#database) below) — never assume a
native UUID column type.

**Search pipeline** (`search` package): a chain of single-responsibility, independently-tested
stages — `DatePruner` (prune candidate files by date before touching the filesystem) →
`GlobFileResolver` (resolve backup file globs) → `LogSourceReader` (`PlainLogSourceReader` /
`GzipLogSourceReader`, streaming reads) → `LogLineParser` (per-project, built from its
`LinePattern` by `LogLineParserFactory`) does a timestamp fast-reject before the more expensive
`FieldMatcher` (`ExactTokenMatcher` / `SubstringMatcher` / `RegexMatcher`, one per `MatchType`) →
`ResultMerger` (merge-sort by timestamp). `SearchServiceImpl` fans this out across a project's
`LogSource` nodes concurrently on **virtual threads** (`Executors.newVirtualThreadPerTaskExecutor()`),
bounded by a `Semaphore` sized from `search.max-nodes-parallel`, and enforces a hard result cap
(`search.max-results`) with a "some nodes were unreachable" warning rather than failing the whole
search. Backup files are scanned oldest→newest, live file last, so results come out chronological.

**Admin wizard** (`project.config.ProjectWizardController`): an HTMX multi-step flow (details →
nodes → sample-line → fields → review) built around one `ProjectWizardForm` draft object held in
the `HttpSession` under key `projectWizardDraft`. Every step handler mutates the draft then calls
the private `step(...)` helper, which **re-sets** the session attribute before rendering. This
re-set is required, not decoration — see [[spring-session-mutation-gotcha]] in memory: Spring
Session JDBC serializes attributes and only persists changes made through `setAttribute`, so
in-place mutation of a session-held object is silently lost without it. `MockMvc`-based tests can
pass even when this is broken, since MockMvc doesn't round-trip through the real session store —
be skeptical of wizard test coverage for this specific failure mode.

**Security** (`SecurityConfig`): a single in-memory admin user built from `AdminProperties`
(`loguty.admin.username` / `loguty.admin.password`), BCrypt-encoded at startup. Public:
`/`, `/search/**`, `/api/search/**`, `/login`, static assets, `/actuator/health`. Everything under
`/admin/**`, `/config/**`, `/api/projects/**` requires authentication. `DevH2ConsoleSecurityConfig`
is a separate, lower-priority filter chain that only applies to `/h2-console/**` and only matters
in the dev profile.

### Database

Flyway migrations live under `src/main/resources/db/migration/`: `common/` is the portable
baseline applied to every engine; `{vendor}/` (currently `postgresql/`) is resolved automatically
by Flyway and holds vendor-specific overrides plus the Spring Session JDBC tables. In dev (H2, in
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
