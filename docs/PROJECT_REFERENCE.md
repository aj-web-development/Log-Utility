# Log Utility — Project Reference

**Purpose of this document:** a single, load-once source of context for making changes to this
codebase — new features, bug fixes, refactors, dead-code removal, dependency upgrades — without
having to re-explore the whole repo first. Read this before starting non-trivial work; update it
when you change something the document describes (architecture, conventions, gotchas, or the
"current state" section). Keep it accurate rather than exhaustive — link to source files instead
of duplicating their content, and delete sections that no longer apply.

Companion docs: [README.md](../README.md) (user-facing: how to run/build/deploy).

---

## 1. What this is

A standalone, multi-project log search tool. An admin configures each target application's logs
once — either by uploading its `logback-spring.xml` or through a manual wizard — and afterwards
anyone can search that project's live and rotated/compressed logs across multiple nodes, filtered
by date range, an identifier field (trace ID, session ID, ...), or free text. **Searching requires
no login; only project configuration does.**

Built in 9 phases per the original spec, all complete as of 2026-07-22. See
[§10 Current state](#10-current-state--whats-not-here) for what's deliberately absent.

## 2. Tech stack & versions (read before assuming anything is "standard Spring Boot 3.x")

- **Java 21** (floor, not a ceiling — code may use virtual threads and other 21-only APIs freely)
- **Spring Boot 4.1.0** → Spring Framework 7, Jakarta EE 11, Spring Security 7, Spring Session
  4.1.0, Hibernate ORM 7. This is *not* the Boot 3.x line most tutorials/training data assume.
- **Maven** (`./mvnw` wrapper). GroupId `com.in10s`, artifactId `logutility`, base package
  `com.in10s.logutility`.
- **PostgreSQL** (prod) / **H2** (dev, `MODE=PostgreSQL`), via **Flyway**.
- **Thymeleaf + HTMX 2.0.3 + Alpine.js 3.x + Tailwind** — all via CDN `<script>` tags in
  [fragments/head.html](../src/main/resources/templates/fragments/head.html), zero Node/frontend
  build step.
- Lombok for entities/services (`@Getter/@Setter/@RequiredArgsConstructor`); **DTOs are Java
  `record`s**, never Lombok classes.

### 2.1 The one thing most likely to bite you: Boot 4's modularized auto-configuration

In Spring Boot 4, auto-configuration for non-core integrations moved out of the starter jars into
separate `spring-boot-<tech>` modules. **Adding a library's raw jar to the classpath is often not
enough to activate it** — there's no exception, no warning, it just silently does nothing. This
already happened four times in this codebase:

| Integration | Symptom when the module was missing | Fix |
|---|---|---|
| Flyway | Zero Flyway log lines at boot; no tables created; app booted "successfully" anyway | add `org.springframework.boot:spring-boot-flyway` |
| Spring Session JDBC | (would have silently used in-memory sessions) | add `org.springframework.boot:spring-boot-session-jdbc` (+ `spring-session-jdbc` for the repo itself) |
| H2 web console | `/h2-console` → 404 even with `spring.h2.console.enabled=true` | add `org.springframework.boot:spring-boot-h2console` |
| MockMvc test support | `@AutoConfigureMockMvc`/`@WebMvcTest` not resolvable from `spring-boot-starter-test` | moved to `spring-boot-webmvc-test`, package `org.springframework.boot.webmvc.test.autoconfigure` — this repo sidesteps it entirely by building `MockMvc` manually: `MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build()` in every controller test's `@BeforeEach` |

**Rule of thumb when adding any new integration that isn't a `spring-boot-starter-*`:** check the
`spring-boot-dependencies` BOM (`~/.m2/repository/org/springframework/boot/spring-boot-dependencies/4.1.0/…pom`)
for a matching `spring-boot-<tech>` artifact, add it, and then **verify the integration actually
activates** (grep the boot log for its startup line, or curl the endpoint) — never assume.

## 3. Build, run, test

```bash
./mvnw spring-boot:run              # dev profile (H2), default — http://localhost:8080
./mvnw clean package                # executable jar → target/logutility-0.0.1-SNAPSHOT.jar
./mvnw clean package -Pwar21        # WAR for an external servlet container → target/logutility-java21.war
./mvnw test                         # full suite
./mvnw test -Dtest=ClassName         # single test class
./mvnw test -Dtest=ClassName#method  # single test method
```

- `war21` requires a JDK 21 entry in `~/.m2/toolchains.xml` (uses `maven-toolchains-plugin`).
- Dev admin login: `admin` / `admin` (from `application-dev.yml`, dev-only).
- Prod requires `SPRING_PROFILES_ACTIVE=prod` + `LOGUTY_ADMIN_USERNAME`/`LOGUTY_ADMIN_PASSWORD`
  (app **fails fast at startup** without them) + `JDBC_DATABASE_URL`/`_USERNAME`/`_PASSWORD`.
  Full details in [README.md](../README.md#configuring-for-production).

### Verification discipline this codebase was built with — keep doing this

Every phase of this build was verified by (1) unit/integration tests, **and** (2) booting the
real app and driving it with `curl` (login flow, wizard steps, multipart upload, search queries)
against real files on disk, reading the **raw HTML response**, not just asserting
`containsString(...)` in MockMvc. This caught two real bugs that a looser test would have missed
(see [§9 Known gotchas](#9-known-gotchas-read-before-touching-these-areas)) — MockMvc's
`webAppContextSetup` doesn't run the real Spring Session servlet filter, and a `containsString`
check can't tell "the expected content is present" apart from "the expected content is present
*alongside content that shouldn't be there*". When you change wizard/search rendering logic,
re-run this kind of check, not just the existing tests.

## 4. Package map

```
com.in10s.logutility
├─ LogutilityApplication      extends SpringBootServletInitializer (WAR-deployable + runnable jar)
├─ project/                   JPA entities + repositories (the domain model)
├─ project/config/            Project CRUD service + the admin setup wizard + its controllers
├─ parser/                    Logback XML parser + sample-line analyzer (heuristic, no ML)
├─ validation/                Path-availability checking ("Test path" button)
├─ search/                    The search engine pipeline (this is the architectural core)
├─ security/                  Single-admin auth config
└─ web/                       Thin Thymeleaf controllers: login, admin landing, public search
```

Layering is strict: `web`/`project.config` controllers → `project.config` services → `project`
repositories → `project` entities. Controllers hold no business logic. Services depend on
interfaces (constructor injection only, no field `@Autowired`, no `new` of collaborators).

## 5. Data model

Four entities in `project/`, all with `UUID` ids stored as **`VARCHAR(36)`** (via
`@JdbcTypeCode(SqlTypes.VARCHAR)`) — deliberately not a native `UUID` column type, because not
every SQL database has one (see [§6 Database portability](#6-database-portability)).

```
Project (name UNIQUE, description, createdAt, updatedAt)
 ├─ 1:N LogSource   (nodeLabel, liveLogPath, backupRootPath, backupPathPattern,
 │                   lastCheckedAt/lastCheckStatus/lastCheckMessage — from "Test path")
 ├─ 1:N FilterField (field_key, label, mdcKey, matchType [EXACT_TOKEN|SUBSTRING|REGEX], linePrefix)
 └─ 1:1 LinePattern (timestampPattern, timestampRegexOrPosition, levelPattern, loggerPattern)
```

`backupPathPattern` uses this app's own placeholder syntax — `{date}` (always `yyyy-MM-dd`),
`{HH}`, `{i}` — e.g. `{date}/app.{HH}.{i}.log.gz`. This is *not* Logback's `%d{...}`/`%i` syntax;
`LogbackXmlParser` converts from Logback's syntax into this one on upload.

Fetch-join query methods on `ProjectRepository` (`findByIdWithFilterFields`,
`findByIdWithLogSources`) avoid N+1 when loading a project for editing or public display; they're
separate methods (not one combined fetch) because Hibernate rejects fetching two `List`
associations in a single query (`MultipleBagFetchException`).

## 6. Database portability

Flyway locations: `classpath:db/migration/common,classpath:db/migration/{vendor}` (set in
[application.yml](../src/main/resources/application.yml)). `common/` is the portable baseline
(runs on every SQL database); `{vendor}` resolves to whatever engine is actually running
(`postgresql`, `h2`, `mysql`, ...) and holds vendor-only overrides. Currently:

- `common/V1__init.sql`, `V2__indexes.sql` — the four tables above.
- `postgresql/V3__spring_session.sql` — Spring Session JDBC tables, copied verbatim from
  `spring-session-jdbc`'s `schema-postgresql.sql`. In prod, Flyway owns these
  (`spring.session.jdbc.initialize-schema: never`); in dev, Spring Session auto-creates them on
  the embedded H2 DB instead (`initialize-schema: embedded`).

**To add support for another database:** add its JDBC driver + `flyway-database-<db>` module,
point `spring.datasource.*` at it, and if you need vendor-specific DDL, add
`db/migration/<db>/Vn__*.sql`. Known gap: SQL Server's `TIMESTAMP` type means "rowversion", not a
date/time column — a SQL Server target needs `db/migration/sqlserver/` using `DATETIME2` instead.

## 7. Security model

One admin account, no user database. Credentials come from `LOGUTY_ADMIN_USERNAME`/
`LOGUTY_ADMIN_PASSWORD` env vars (bound via `loguty.admin.*` in
[AdminProperties](../src/main/java/com/in10s/logutility/security/AdminProperties.java)), BCrypt-hashed
into an in-memory `UserDetailsService` at startup — **the raw password is never persisted**.
[SecurityConfig](../src/main/java/com/in10s/logutility/security/SecurityConfig.java) fails fast
(`IllegalStateException`) if either is blank.

Access rules: `permitAll` on `/`, `/search/**`, `/api/search/**`, `/login`, static assets, health;
`authenticated` on `/admin/**`, `/config/**`, `/api/projects/**`. A separate
[DevH2ConsoleSecurityConfig](../src/main/java/com/in10s/logutility/security/DevH2ConsoleSecurityConfig.java)
(`@Profile("dev")`, `@Order(1)`) permits `/h2-console/**` and relaxes CSRF/frame-options for it
only — never active in prod.

Sessions: Spring Session JDBC (not in-memory), so the admin stays logged in across multiple app
instances sharing one database. **See §9 for the mutation gotcha this creates.**

## 8. The wizard system

[ProjectWizardController](../src/main/java/com/in10s/logutility/project/config/ProjectWizardController.java)
drives a 5-step HTMX wizard, session-backed. Steps, in order (see
[WizardStep](../src/main/java/com/in10s/logutility/project/config/WizardStep.java)):

```
DETAILS → NODES → SAMPLE_LINE → FIELDS → REVIEW
```

- The working draft is one `ProjectWizardForm` object stored under session key
  `"projectWizardDraft"`. Every step POST binds that step's fields into a fresh
  `@ModelAttribute ProjectWizardForm submitted`, merges just the relevant part into the session
  draft, and returns the `admin/projects/steps :: stepBody` fragment for HTMX to swap in.
- Three entry points seed a fresh draft: `GET /admin/projects/new` (blank), `GET
  /admin/projects/{id}/edit` (loaded from the DB via `ProjectService.loadForEdit`), and `POST
  /admin/projects/upload` (seeded from a parsed `logback-spring.xml` — MDC keys become
  `FilterField` rows, the rolling pattern becomes one node's `backupPathPattern`; live/backup root
  paths are deliberately left **blank**, since those are env-resolved per real node and the XML
  can't know them — only shown as an informational hint banner on the Nodes step).
  All three still land on `DETAILS`, since only the admin can supply the project name.
- `POST /wizard/save` is the only step that actually writes to the database
  (`ProjectService.saveFromWizard`) — every other step only touches the session draft.
- The Nodes step's "Test path" button and the Sample-line step's "Analyze" button are both
  HTMX calls that **do not advance the wizard** — they re-render the current step in place with
  updated data (a live path-reachability badge, or fresh timestamp/level/logger suggestions).
- All wizard form classes (`ProjectWizardForm`, `NodeForm`, `FilterFieldForm`, `LinePatternForm`)
  implement `Serializable` — required because the session is Spring Session JDBC, not in-memory.

## 9. Known gotchas (read before touching these areas)

**Spring Session only persists attributes you explicitly re-`setAttribute`.** Mutating an object
retrieved via `session.getAttribute(...)` in place does **not** get written back to the JDBC
session store — the next request reloads the stale, unmutated version from the DB. This is why
`ProjectWizardController`'s private `step(...)` helper always calls
`session.setAttribute(DRAFT_KEY, draft)` before returning, even though `draft` was already
mutated by reference. **If you add a new session-backed mutable flow, follow this pattern.**
MockMvc built with `webAppContextSetup(...).apply(springSecurity())` does *not* run the real
session filter, so it holds the same object reference across "requests" and will pass even if
this bug is present — verify session-backed flows with real `curl` + cookie jar, not just MockMvc.

**Thymeleaf: never put `th:insert`/`th:replace` on the same tag as `th:switch`/`th:case`/`th:if`/
`th:with`.** Fragment-inclusion attributes have higher processing precedence, so they execute
*before* the conditional gets a chance to prune non-matching branches — every branch's fragment
gets inserted unconditionally. This produced a real bug in `steps.html`'s step switch (every
wizard step rendered *all* steps' forms stacked together; only the currently-active step's
progress-bar highlighting looked right, since that's driven by an unrelated loop). Fix: always
nest — conditional on an outer tag, `th:replace` on an inner child:
```html
<div th:case="'X'"><div th:replace="~{... :: frag}"></div></div>
```
When testing a step/state-branching fragment, assert the *absence* of sibling branches' content,
not just the presence of the expected content — a plain `containsString` won't catch this class
of bug (the real content is genuinely there, just with extra content alongside it).

**Java's `DateTimeFormatter.parseUnresolved` needs `YEAR_OF_ERA` fallback.** A `"yyyy"` pattern
token resolves to `ChronoField.YEAR_OF_ERA`, not `YEAR` (`"uuuu"` would give `YEAR`) —
`parseUnresolved` skips era resolution, so reading only `ChronoField.YEAR` silently fails for the
overwhelmingly common `yyyy-MM-dd` pattern. `DefaultLogLineParser.toLocalDateTime` checks `YEAR`
then falls back to `YEAR_OF_ERA`.

**Maven Central artifact that looks like it should exist but doesn't:** `flyway-database-h2`.
H2 support ships inside `flyway-core` itself; only `flyway-database-postgresql` (and other
non-default engines) are separate artifacts.

## 10. Current state / what's *not* here

All 9 phases of the original build spec are implemented and tested (skeleton → security → project
CRUD/wizard → path validation → Logback XML upload → search engine → public search UI →
sample-line analyzer → docs). Deliberately out of scope / not implemented:

- No JSON/API layer for search — `/api/search/**` is reserved in `SecurityConfig` but nothing is
  mapped there yet; the search UI is server-rendered Thymeleaf + HTMX only.
- No multi-admin / role system — exactly one admin account, by design.
- No automatic vendor override beyond PostgreSQL — MySQL/Oracle/SQL Server would each need their
  own `db/migration/<vendor>/` folder and a Flyway/driver dependency added (see §6).
- Sample-line analyzer's `loggerPattern`/`timestampRegexOrPosition` are captured and persisted but
  **not currently consumed** by the search engine (`LogLineParserFactory` only reads
  `timestampPattern` and `levelPattern`) — they exist for the wizard's confirm/correct UI and
  potential future use.
- No CI/CD config, no Docker/containerization files.

## 11. The search engine pipeline (the architectural core — `search/`)

One `SearchService.search(SearchRequest) -> SearchResult` call runs this pipeline:

1. **[ProjectSearchLoader](../src/main/java/com/in10s/logutility/search/ProjectSearchLoader.java)**
   — a separate `@Transactional(readOnly=true)` bean (not a self-invocation, so the proxy applies)
   that loads the project's nodes/fields/line-pattern fully inside one transaction, before the
   async fan-out — nothing lazy is touched off-session.
2. **[DatePruner](../src/main/java/com/in10s/logutility/search/DatePruner.java)** — pure,
   filesystem-free: expands `{date}` per calendar day (assumes `yyyy-MM-dd` folder format — a
   documented limitation, not configurable per-project), turns `{HH}`/`{i}` into `*` globs, and
   decides whether to also read the live file via an injectable `Clock` (testable).
3. **[GlobFileResolver](../src/main/java/com/in10s/logutility/search/GlobFileResolver.java)**
   resolves each glob into concrete files by walking one path segment at a time via
   `Files.newDirectoryStream` — cross-platform, unlike a whole-path `glob:` `PathMatcher`.
4. **[LogSourceReader](../src/main/java/com/in10s/logutility/search/LogSourceReader.java)**
   (plain/`.log` and gzip/`.log.gz` impls) streams lines lazily via `BufferedReader` — never
   buffers a whole file.
5. **Per-line filtering** in
   [SearchServiceImpl](../src/main/java/com/in10s/logutility/search/SearchServiceImpl.java):
   [LogLineParser](../src/main/java/com/in10s/logutility/search/LogLineParser.java) parses only
   the leading timestamp (via `parseUnresolved`, never throws) and rejects out-of-range lines
   *before* running the more expensive field/text matchers — the fast-reject.
6. **[FieldMatcher](../src/main/java/com/in10s/logutility/search/FieldMatcher.java)** strategy —
   `ExactTokenMatcher`/`SubstringMatcher`/`RegexMatcher`, selected per `FilterField.matchType` via
   an injected `Map<MatchType, FieldMatcher>` (add a new match type = add a new `@Component`
   implementing the interface + a new `MatchType` enum value — nothing else changes).
7. **Fan-out**: `Executors.newVirtualThreadPerTaskExecutor()`, one virtual thread per node,
   bounded by `Semaphore(search.max-nodes-parallel)`. Each node re-checks reachability first
   (`PathAvailabilityChecker`) — unreachable nodes are added to `unreachableNodes` and skipped,
   never fail the whole search.
8. **[ResultMerger](../src/main/java/com/in10s/logutility/search/ResultMerger.java)** —
   timestamp-sorted merge (nulls last) of every node's matches.
9. **Cap**: hard-stops at `search.max-results` (default 5000; configurable via
   `SEARCH_MAX_RESULTS`/`SEARCH_MAX_NODES_PARALLEL` env vars, standard Spring relaxed binding) and
   sets `truncated=true` rather than returning everything.

`LogLineParserFactory` builds one `LogLineParser` per search (compiled formatter/regex reused
across every line, not recompiled per line) — falls back to a short list of common timestamp
formats when a project has no `LinePattern.timestampPattern` configured yet.

## 12. Frontend conventions

- [fragments/head.html](../src/main/resources/templates/fragments/head.html) is `th:replace`d
  into every page's `<head>` — pulls in Tailwind/HTMX/Alpine CDN scripts, exposes the CSRF token
  as `<meta>` tags plus an `htmx:configRequest` listener that auto-attaches it to every HTMX
  request, and defines the `.htmx-indicator` / `[x-cloak]` CSS conventions used across the app.
- Active-project selection (used by both the admin project list's "Set active" button and the
  public search page's project switcher) is a **shared cookie**,
  `ProjectAdminController.ACTIVE_PROJECT_COOKIE` (`LOGUTY_ACTIVE_PROJECT`) — not tied to the login
  session, since searching is public. Reused by
  [SearchController](../src/main/java/com/in10s/logutility/web/SearchController.java) directly by
  referencing the same constant.
- HTMX `hx-vals` with the `js:` prefix (e.g. `hx-vals="js:{page: ${page - 1}}"` via Thymeleaf's
  `|...|` literal substitution) is the established pattern for passing dynamic values that don't
  come from a form field's own `name` — used for pagination and the wizard's per-node "Test path"
  button (which reads live `<input>` values by id via `document.getElementById(...)`, since the
  path inputs' real `name` attributes are indexed list-binding names like
  `nodes[0].liveLogPath`, not `path`).
- Public-facing Thymeleaf models use small `record` DTOs (`PublicProjectView`,
  `PublicFilterFieldView`, `ProjectSummaryDto`) — JPA entities are never passed to a template.
