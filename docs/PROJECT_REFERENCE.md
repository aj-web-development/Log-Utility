# Log Utility — Full Backend + Frontend Flow Reference

**Purpose of this document:** a single, comprehensive, load-once source of truth for the entire
application — every backend layer and every frontend behavior, in enough detail that a future
change can be made without re-reading the whole codebase first. This is a **living document**:

> **Whenever you (Claude) make a change that alters something this document describes — a new
> endpoint, a changed flow, a new entity/field, a renamed package, a new config property, a new
> frontend behavior, a new gotcha — update the relevant section of this file in the same piece of
> work.** Treat an out-of-date section here as a bug. If you're about to start non-trivial work,
> read this file first; if you finish work that makes a section wrong, fix that section before
> considering the task done.

Companion docs: [README.md](../README.md) (user-facing: how to run/build/deploy),
[CLAUDE.md](../CLAUDE.md) (short-form guidance for Claude Code — this document is the expanded
version of the same material).

Last verified against the code: 2026-07-23.

---

## 1. What this is

A standalone Spring Boot app for searching another application's log files. An admin configures a
target application's logs once (upload its `logback-spring.xml` or use the guided setup wizard),
and afterwards anyone can search that project's live and rotated/gzip-compressed logs — across
multiple nodes, each of which may write more than one distinct log output (e.g. `app.log`,
`error.log`, `access.log`) — filtered by date range, by a configured identifier field (trace id,
session id, ...), or by free text. Search requires no login. Only project configuration sits
behind a single admin account.

The app exposes **two parallel front doors** to the same underlying services:

1. A server-rendered Thymeleaf + HTMX + Alpine.js UI (public search page, admin wizard).
2. A JSON REST API (`/api/**`), including a Server-Sent-Events streaming search endpoint and a
   plain-text export endpoint, documented via springdoc-openapi (`/swagger-ui.html`).

## 2. Tech stack & versions

- **Java 21** (floor, not ceiling — virtual threads and other 21-only APIs are used freely).
- **Spring Boot 4.1.0** → Spring Framework 7, Jakarta EE 11, Spring Security 7, Spring Session
  4.1.0, Hibernate ORM 7. Not the Boot 3.x line most tutorials/training data assume.
- **Maven** (`./mvnw` / `mvnw.cmd`). GroupId `com.app`, artifactId `logutility`, base package
  `com.app.logutility`.
- **springdoc-openapi-starter-webmvc-ui** (3.0.3) — auto-generates `/v3/api-docs` and
  `/swagger-ui.html` from the REST controllers' `@Operation`/`@Tag` annotations.
- **PostgreSQL** (prod) / **H2** (dev, `MODE=PostgreSQL`), via **Flyway**.
- **Thymeleaf + HTMX 2.0.3 + Alpine.js 3.x + Tailwind** — all via CDN `<script>` tags in
  [fragments/head.html](../src/main/resources/templates/fragments/head.html), zero Node/frontend
  build step.
- Lombok (`@Getter`/`@Setter`/`@RequiredArgsConstructor`) on entities, form-backing objects, and
  services. **DTOs crossing an HTTP boundary are Java `record`s**, never Lombok classes.

### 2.1 Spring Boot 4's modularized auto-configuration — read this before adding any dependency

In Spring Boot 4, auto-configuration for non-core integrations moved out of the starter jars into
separate `spring-boot-<tech>` modules. **Adding a library's raw jar to the classpath is often not
enough to activate it** — no exception, no warning, it just silently does nothing. This has already
happened four times in this codebase:

| Integration | Symptom when the module was missing | Fix |
|---|---|---|
| Flyway | Zero Flyway log lines at boot; no tables created; app "boots successfully" anyway | add `org.springframework.boot:spring-boot-flyway` |
| Spring Session JDBC | Would silently fall back to in-memory sessions | add `org.springframework.boot:spring-boot-session-jdbc` (+ `spring-session-jdbc` for the repository itself) |
| H2 web console | `/h2-console` → 404 even with `spring.h2.console.enabled=true` | add `org.springframework.boot:spring-boot-h2console` |
| MockMvc test support | `@AutoConfigureMockMvc`/`@WebMvcTest` unresolvable from `spring-boot-starter-test` | moved to `spring-boot-webmvc-test` — this repo sidesteps it entirely by building `MockMvc` manually: `MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build()` in every controller test's `@BeforeEach` |

**Rule of thumb:** when adding any new integration that isn't a `spring-boot-starter-*`, check the
`spring-boot-dependencies` BOM for a matching `spring-boot-<tech>` artifact, add it, and verify the
integration actually activates (grep the boot log, or curl the endpoint) — never assume. Also:
`flyway-database-h2` **does not exist** as a Maven artifact — H2 support ships inside
`flyway-core` itself; only non-default engines (`flyway-database-postgresql`, etc.) are separate.

## 3. Build, run, test

```bash
mvnw.cmd spring-boot:run              # dev profile (H2), default — http://localhost:8080
mvnw.cmd clean package                # executable jar → target/logutility-0.0.1-SNAPSHOT.jar
mvnw.cmd clean package -Pwar21        # WAR for an external servlet container → target/logutility-java21.war
mvnw.cmd test                         # full suite
mvnw.cmd test -Dtest=ClassName        # single test class
mvnw.cmd test -Dtest=ClassName#method # single test method
```

- `war21` requires a JDK 21 entry in `~/.m2/toolchains.xml` (uses `maven-toolchains-plugin`); WAR
  packaging marks embedded Tomcat `provided` since the external container supplies it.
- Dev admin login: `admin` / `admin` (from `application-dev.yml`, dev-profile only, throwaway).
- Prod requires `SPRING_PROFILES_ACTIVE=prod` + `LOGUTY_ADMIN_USERNAME`/`LOGUTY_ADMIN_PASSWORD`
  (app **fails fast at startup** without them, `SecurityConfig#userDetailsService`) +
  `JDBC_DATABASE_URL`/`_USERNAME`/`_PASSWORD`.
- No CI/CD config and no Docker/containerization files exist in this repo.

Most tests are fast unit tests with no Spring context (search pipeline, Logback XML parser,
sample-line analyzer). `*IntegrationTest` classes boot the full app against the H2 dev profile.
`config.security.SecurityIntegrationTest` covers both filter chains.

## 4. Package map — layer first, then domain

`com.app.logutility` is organized **by technical layer, then by domain** — e.g. `controller.project`,
`entity.project`, `service.project`, and `repository.project` are four separate top-level packages,
not one `project` package. The domain name is always the *second* path segment.

```
com.app.logutility
├─ LogutilityApplication        extends SpringBootServletInitializer (WAR-deployable + runnable jar)
├─ config
│   ├─ security   AdminProperties, SecurityConfig (2 filter chains), CorsConfig, DevH2ConsoleSecurityConfig
│   ├─ search     SearchProperties (search.* tuning), SearchConfig (Clock + Semaphore beans)
│   └─ openapi    OpenApiConfig (springdoc metadata + basic-auth security scheme)
├─ controller
│   ├─ web        AdminController (/admin landing), LoginController (/login page)
│   ├─ project    ProjectAdminController (list/activate/delete), ProjectWizardController (HTMX wizard),
│   │              ProjectApiController (REST CRUD + helpers)
│   ├─ search     SearchController (public HTMX search page), SearchApiController (REST search),
│   │              SearchStreamController (SSE + plain-text export)
│   └─ common     ApiExceptionHandler (@RestControllerAdvice for the 3 REST controllers above),
│                  ApiErrorAttributes (reshapes Boot's default /error body for unmatched /api/** routes)
├─ entity.project      Project, LogSource, LogFile, FilterField, LinePattern, MatchType, CheckStatus
├─ repository.project  ProjectRepository, LogSourceRepository, LogFileRepository,
│                       FilterFieldRepository, LinePatternRepository
├─ request
│   ├─ project    ProjectWizardForm, NodeForm, LogFileForm, FilterFieldForm, LinePatternForm (session-held,
│   │              mutable, Serializable — the wizard draft) + ProjectRequest, NodeRequest, LogFileRequest,
│   │              FilterFieldRequest, LinePatternRequest, PathCheckRequest (REST request records)
│   ├─ parser     SampleLineRequest
│   └─ search     SearchRequest
├─ response
│   ├─ project    ProjectDetailResponse, NodeResponse, LogFileResponse, FilterFieldResponse,
│   │              LinePatternResponse, PathCheckOutcome, ProjectSummaryDto, PublicProjectView,
│   │              PublicFilterFieldView, WizardStep (enum)
│   ├─ parser     LogbackParseResult, MdcFieldSuggestion, SampleLineAnalysis, HighlightSegment, TokenMatch
│   ├─ search     LogLine, SearchResult, SearchSummary, SearchProgress
│   ├─ validation PathCheckResult
│   └─ common     ApiError
├─ dto.search     ScanPlan (internal-only, never serialized over HTTP)
├─ exception
│   ├─ project    ProjectNotFoundException
│   ├─ parser     LogbackParseException
│   └─ search     SearchOverloadedException
└─ service
    ├─ project    ProjectService / ProjectServiceImpl, ProjectWizardValidation
    ├─ parser     LogbackXmlParser / LogbackXmlParserImpl, SampleLineAnalyzer / SampleLineAnalyzerImpl
    ├─ search     SearchService / SearchServiceImpl + the whole pipeline (§8)
    └─ validation PathAvailabilityChecker / PathAvailabilityCheckerImpl
```

Layering is strict: controllers → services → repositories → entities. Controllers hold no business
logic. Services use constructor injection only (Lombok `@RequiredArgsConstructor`, no field
`@Autowired`, no `new` of collaborators).

## 5. Data model

Five entities under `entity.project`, all with `UUID` ids stored as `VARCHAR(36)` via
`@JdbcTypeCode(SqlTypes.VARCHAR)` — deliberately not a native `UUID` column type, so the schema
stays portable to databases without one (MySQL, Oracle, SQL Server).

```
Project (name UNIQUE, description, createdAt, updatedAt)
 ├─ 1:N LogSource   (nodeLabel — just a node identity)
 │       └─ 1:N LogFile (fileLabel, liveLogPath, backupRootPath, backupPathPattern,
 │                       lastCheckedAt, lastCheckStatus [REACHABLE|UNREACHABLE|UNKNOWN], lastCheckMessage)
 ├─ 1:N FilterField (field_key, label, mdcKey, matchType [EXACT_TOKEN|SUBSTRING|REGEX], linePrefix)
 └─ 1:1 LinePattern (timestampPattern, timestampRegexOrPosition, levelPattern, loggerPattern)
```

- `Project.addLogSource`/`addFilterField`/`setLinePattern` are the only mutators that should be
  used to build the graph — each keeps the inverse side (`project` back-reference) consistent.
  Likewise `LogSource.addLogFile` keeps `LogFile.logSource` consistent.
- A node (`LogSource`) can write more than one distinct log output; each is its own `LogFile` row
  with its own live path, backup root, backup pattern, and cached path-check result. This split was
  introduced by migration `V4__log_file.sql` — before it, `LogSource` itself carried a single
  live/backup/pattern config, which the migration carries forward as each node's first `LogFile`,
  labeled `"Application"`.
- `backupPathPattern` uses this app's own placeholder syntax — `{date}` (always `yyyy-MM-dd`),
  `{HH}`, `{i}` — e.g. `{date}/app.{HH}.{i}.log.gz`. **Not** Logback's `%d{...}`/`%i` syntax;
  `LogbackXmlParser` converts from Logback's syntax into this one on upload (§9.5).
- `ProjectRepository.findByIdWithFilterFields` / `.findByIdWithLogSources` are separate fetch-join
  queries (not one combined query) because Hibernate rejects fetching two `List` associations in a
  single query (`MultipleBagFetchException`).
- `ProjectRepository.findAllSummaries()` returns `ProjectSummaryDto` via a JPQL constructor
  expression with count subqueries — one query for the whole admin list, no N+1.

## 6. Two front doors, two security filter chains

`SecurityConfig` defines two independent `SecurityFilterChain` beans, each scoped by
`HttpSecurity#securityMatcher` so requests are routed to exactly one:

- **`apiSecurityFilterChain`** (`@Order(2)`, matches `/api/**`) — stateless HTTP Basic, no
  session (`SessionCreationPolicy.STATELESS`), CSRF disabled (a cookie-less `Authorization` header
  on every request doesn't need CSRF protection). `/api/search/**` is `permitAll`; everything else
  under `/api/**` (i.e. `/api/projects/**`) requires the admin account via HTTP Basic.
- **`appSecurityFilterChain`** (`@Order(3)`, everything else) — the server-rendered Thymeleaf/HTMX
  UI. Public: `/`, `/search/**`, `/login`, `/error`, static assets, `/actuator/health`,
  `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. Protected:
  `/admin/**`, `/config/**`, and anything else, via Spring Security form login
  (`loginPage("/login")`, `defaultSuccessUrl("/admin", true)`) and Spring Session JDBC.
- **`DevH2ConsoleSecurityConfig`** (`@Order(1)`, `@Profile("dev")`) — a third chain, matching only
  `/h2-console/**`, permitting all access and relaxing CSRF/frame-options so the console (which
  renders in an iframe) works. Never active outside the dev profile.

Both the API and app chains share one in-memory `UserDetailsService` (`SecurityConfig`), built from
`AdminProperties` (`loguty.admin.username`/`.password`, env vars `LOGUTY_ADMIN_USERNAME`/
`LOGUTY_ADMIN_PASSWORD` in prod), BCrypt-encoded once at startup. `SecurityConfig` throws
`IllegalStateException` at bean-creation time if either is blank — a production instance can
never come up with an unauthenticated or blank admin account.

**CORS**: off by default. `CorsConfig` registers a `CorsConfigurationSource` for `/api/**` only,
driven by the (empty-by-default) property `loguty.api.cors.allowed-origins` (comma-separated). No
browser-based external UI can call the API cross-origin until an admin explicitly configures it.

**OpenAPI**: `OpenApiConfig` declares one global `basicAuth` security scheme so Swagger UI's "Try it
out" can authenticate against `/api/projects/**`; `/api/search/**` endpoints are annotated
`@SecurityRequirements` (empty) to show as public in the generated docs.

## 7. Request flows

### 7.1 Public search page (`GET /`, `GET /search`)

Handled by `controller.search.SearchController`.

1. `GET /` (`index`) reads the active-project cookie `LOGUTY_ACTIVE_PROJECT`
   (`ProjectAdminController.ACTIVE_PROJECT_COOKIE` — shared constant, not tied to any login
   session since searching is public), loads all projects (`ProjectService.listProjects()`) for
   the picker `<select>`, and if a valid active project is set, loads its public view
   (`ProjectService.findPublicView` → `PublicProjectView`, containing just `id`, `name`, and its
   `FilterField`s as `PublicFilterFieldView(key, label)` — **never the raw JPA entity**). Renders
   `search.html` with `defaultFrom`/`defaultTo` (now − 1 day .. now) pre-filled into the date
   inputs.
2. Changing the project `<select>` submits a plain `GET /search/select-project?projectId=...`
   (`selectProject`), which just rewrites the cookie and redirects to `/` — not HTMX, a full
   navigation, since switching projects is infrequent.
3. Submitting the search form is HTMX (`hx-get="/search" hx-target="#results"`), hitting
   `SearchController.search`: reads the active-project cookie (the `projectId` hidden field in the
   form is *not* read by this endpoint — it exists only so client-side `downloadResults()` can read
   it via `FormData` for the export link, which unlike `/search` needs an explicit `projectId`
   parameter), collects every `filter_<key>` request parameter into a `Map<String,String>`, builds
   a `SearchRequest`, and calls `SearchService.search(...)`. Returns just the
   `search :: resultsFragment` fragment (paginated, `PAGE_SIZE = 50`, page-based, batch — **not**
   the SSE endpoint; see §7.4 for why the streaming API exists separately).
4. Errors (`IllegalArgumentException` from a bad date range, or `SearchOverloadedException` from
   the global concurrency cap) are caught and rendered as an inline banner in the same fragment,
   never a 500 page.

Everything below the HTMX-swapped fragment (view mode, group-by, panel-collapse) is pure
client-side JS in `search.html` — see §10.

### 7.2 Admin login (`GET /login`, form POST `/login`, `/logout`)

`controller.web.LoginController` just renders `login.html`; the actual authentication is Spring
Security's built-in `formLogin` processing filter (configured in `appSecurityFilterChain`, not a
custom controller). `login.html` shows `?error`/`?logout` query-param banners. Successful login
redirects to `/admin` (`AdminController.index`, which just reads the `Authentication` principal
name into the model — this endpoint's only job is to prove the chain requires auth).

### 7.3 Admin project list (`GET/POST /admin/projects/**` via `ProjectAdminController`)

- `GET /admin/projects` (`list`) — reads the same active-project cookie, lists all projects
  (`ProjectSummaryDto`), renders `admin/projects/list.html`.
- `POST /admin/projects/{id}/activate` — sets the cookie (30-day `Max-Age`), returns just the
  `projectList` fragment (HTMX `outerHTML` swap) so the "active" badge updates without a reload.
- `POST /admin/projects/{id}/delete` — deletes via `ProjectService.deleteProject` (cascades to
  `LogSource`/`LogFile`/`FilterField`/`LinePattern` via `orphanRemoval`); if the deleted project was
  the active one, expires the cookie (`Max-Age=0`) so the public search page doesn't keep pointing
  at a project that no longer exists.

### 7.4 Admin project wizard (`ProjectWizardController`, session-backed HTMX flow)

Steps, in order (`response.project.WizardStep`): **DETAILS → NODES → SAMPLE_LINE → FIELDS →
REVIEW**. A single `ProjectWizardForm` draft lives in the `HttpSession` under key
`"projectWizardDraft"` for the whole flow.

**Entry points** (all seed a fresh draft and land on `DETAILS`, since only the admin can supply the
project name):
- `GET /admin/projects/new` — blank draft, one empty `NodeForm` (itself pre-seeded with one empty
  `LogFileForm`) and one empty `FilterFieldForm`, so the first render already shows one row of each.
- `GET /admin/projects/{id}/edit` — `ProjectService.loadForEdit(id)` rebuilds a `ProjectWizardForm`
  from the persisted `Project` graph (including each node's `LogFile`s and their
  `logFileId`/`lastCheckStatus`/`lastCheckMessage`, so previously-recorded path checks still show).
  Guarantees at least one blank node/output/field row exists so the templates always have
  something to render an "add" affordance against.
- `GET /admin/projects/upload` → `POST /admin/projects/upload` (multipart) — parses the uploaded
  XML via `LogbackXmlParser.parse` (§9.5), seeds a fresh draft with one node whose first `LogFile`
  gets the derived `backupPathPattern`, and one `FilterFieldForm` per detected MDC key
  (`MatchType.EXACT_TOKEN` by default). `uploadLiveLogHint`/`uploadBackupRootHint` carry the raw
  detected live-file path and backup-root prefix as **informational-only** text shown on the Nodes
  step — never bound into the real path fields, since those must be the *real*, per-node
  filesystem paths the admin supplies, not whatever the XML happened to say.

**Every step POST** follows the same shape: bind the step's own fields into a fresh
`@ModelAttribute ProjectWizardForm submitted`, merge just the relevant part into the session draft,
call the private `step(...)` helper (which **re-sets** the session attribute — see the gotcha in
§11), and return the `admin/projects/steps :: stepBody` fragment for HTMX to swap into
`#wizard-body`.

- **DETAILS** (`/wizard/details`) — validates non-blank name + not-a-duplicate
  (`ProjectWizardValidation.validateDetails` + `ProjectService.nameExists`), advances to NODES.
- **NODES** (`/wizard/nodes*`) — nodes are now *nested*: each `NodeForm` holds a
  `List<LogFileForm>`. Separate add/remove endpoints exist at both levels:
  `/wizard/nodes/add|remove` (whole node) and
  `/wizard/nodes/{nodeIndex}/outputs/add|remove` (one log output within a node). The **"Test
  paths"** button (`POST /wizard/nodes/test-path`, not a step-advancing endpoint) reads the live
  DOM values of a specific row's live/backup path inputs via `document.getElementById(...)` (their
  real `name` is an indexed list-binding path like `nodes[0].logFiles[0].liveLogPath`, not a plain
  `path`, so HTMX's `hx-vals="js:{...}"` reads them by id instead), checks both paths together
  (`ProjectService.checkPaths`), and — if the row corresponds to an already-persisted `LogFile`
  (`logFileId` present) — records the combined result onto that row immediately, even before the
  wizard is saved. Returns just the `path-badge :: badge` fragment for that row. Advancing
  (`/wizard/nodes`) requires at least one node with a non-blank label
  (`ProjectWizardValidation.validateNodes`).
- **SAMPLE_LINE** (`/wizard/sample-line*`) — optional step. `/wizard/sample-line/analyze` re-runs
  `SampleLineAnalyzer.analyze` against whatever sample line is currently in the textarea and
  overwrites the suggested timestamp/level/logger pattern fields with fresh guesses, staying on the
  same step so the admin can review the highlighted preview (color-coded segments: yellow =
  timestamp, green = level, blue = logger) before confirming or hand-editing. Never fails — an
  unparseable or blank line just yields an "nothing detected" result.
- **FIELDS** (`/wizard/fields*`) — add/remove filter-field rows; advancing validates that any
  partially-filled row has both a key and a label (`ProjectWizardValidation.validateFields`) —
  fields are optional overall, but not half-filled.
- **REVIEW** (`/wizard/review/back`, `/wizard/save`) — read-only summary of the whole draft.
  `POST /wizard/save` re-validates details + nodes (defense in depth — the client can't be trusted
  to have gone through every step honestly), persists via
  `ProjectService.saveFromWizard(draft)`, clears the session draft, and responds with an
  `HX-Redirect: /admin/projects` header (HTMX performs the client-side redirect; the fragment body
  itself — `steps :: saved` — is never actually shown).

`ProjectApiController`'s create/update endpoints (§7.5) are a one-shot equivalent of this entire
flow — one complete `ProjectRequest` payload validated with the same `ProjectWizardValidation` and
persisted with the same `ProjectService.saveFromWizard`, just without the session/multi-step
machinery.

### 7.5 REST API (`/api/**`)

| Controller | Method + path | Auth | Behavior |
|---|---|---|---|
| `SearchApiController` | `GET /api/search/projects` | public | `List<ProjectSummaryDto>` |
| `SearchApiController` | `GET /api/search/projects/{id}` | public | `PublicProjectView` or 404 (`ProjectNotFoundException`) |
| `SearchApiController` | `POST /api/search` | public | Body = `SearchRequest`; batch, returns `SearchResult` (one page) |
| `SearchStreamController` | `GET /api/search/stream` | public | SSE; query params mirror `POST /api/search` (`filter_<key>=<value>` for filters); emits `chunk` events (`List<LogLine>`, batched by `search.chunk-size`), `progress` events (`SearchProgress`), then one `done` event (`SearchSummary`) |
| `SearchStreamController` | `GET /api/search/export` | public | Same params as `/stream`; streams matched raw lines as a `text/plain` attachment (`search-results-<date>.log`) directly to the response writer — never buffers the whole result set. **This is what the search UI's "Download all matches" link actually calls.** |
| `ProjectApiController` | `GET /api/projects` | HTTP Basic | `List<ProjectSummaryDto>` |
| `ProjectApiController` | `GET /api/projects/{id}` | HTTP Basic | `ProjectDetailResponse` (full config, edit-prefill equivalent) or 404 |
| `ProjectApiController` | `POST /api/projects` | HTTP Basic | Body = `ProjectRequest`; validates, `saveFromWizard`, 201 + `Location` header |
| `ProjectApiController` | `PUT /api/projects/{id}` | HTTP Basic | Same shape as create; nodes/fields are **replaced wholesale**, not merged |
| `ProjectApiController` | `DELETE /api/projects/{id}` | HTTP Basic | 204, idempotent (deleting an already-missing id still returns 204) |
| `ProjectApiController` | `POST /api/projects/logback/parse` | HTTP Basic | Multipart `file`; returns `LogbackParseResult` |
| `ProjectApiController` | `POST /api/projects/sample-line/analyze` | HTTP Basic | Body = `SampleLineRequest`; returns `SampleLineAnalysis` |
| `ProjectApiController` | `POST /api/projects/path-check` | HTTP Basic | Body = `PathCheckRequest`; returns `PathCheckOutcome`; `logFileId` optional (records the result if present) |

`{id}` path variables are constrained to a literal UUID-shape regex
(`ProjectApiController.UUID_PATTERN`, duplicated manually in `SearchApiController` — "kept in sync
manually", per its own comment) so they can never structurally collide with a same-depth literal
action route (e.g. `GET /api/projects/path-check` would otherwise 200 with `id="path-check"`
instead of correctly 405-ing).

**Important, non-obvious fact:** the built-in Thymeleaf search page does **not** use the SSE
`/api/search/stream` endpoint for its results — it still uses the classic paginated,
batch `SearchController.search` → `SearchService.search()` path (§7.1). The SSE endpoint exists as
a public API feature for *external* consumers who want progressive results, and the plain-text
`/export` endpoint (same streaming code path) is the one piece of it the built-in UI actually calls
(the "Download all matches" link). If a future task wires the main UI to consume `/stream`
directly, update this note.

`ApiExceptionHandler` (`controller.common`, `@RestControllerAdvice(assignableTypes = {
SearchApiController.class, SearchStreamController.class, ProjectApiController.class })`) is the
**only** place REST error mapping happens; extend its `assignableTypes` list, not a new advice
class, when adding another `@RestController`. Mappings: `ProjectNotFoundException`→404,
`LogbackParseException`/`IllegalArgumentException`→400, `IllegalStateException` (duplicate
name)→409, `HttpMessageNotReadableException` (malformed body)→400,
`MethodArgumentTypeMismatchException`→400, `MissingServletRequestParameterException`→400,
`HttpRequestMethodNotSupportedException`→405, `SearchOverloadedException`→429, anything else→500
(logged at `ERROR`). Body shape is always `ApiError(timestamp, status, error, message, path)`.

`ApiExceptionHandler` only runs once a request has been matched to *one of its listed controllers'*
handler methods. A request under `/api/**` that matches no route at all (typo'd path) or fails
before a handler method is even invoked instead falls through to Spring Boot's default `/error`
handling, whose JSON body doesn't match `ApiError`'s shape. **`ApiErrorAttributes`**
(`controller.common`, extends `DefaultErrorAttributes`) exists specifically to catch that gap: it
reshapes only `/api/**`-prefixed error responses into the same `ApiError` field set (and, like
`ApiExceptionHandler`, never leaks a raw exception message for a 5xx), leaving the Thymeleaf UI's
own `/error` rendering untouched.

## 8. The search engine pipeline (`service.search` — the architectural core)

One `SearchService.search(SearchRequest)` (batch) or `.searchStreaming(SearchRequest, onChunk,
onProgress, onComplete)` (chunked callbacks) call runs this pipeline, fanned out **one virtual
thread per (`LogSource`, `LogFile`) pair** via `Executors.newVirtualThreadPerTaskExecutor()`:

1. **`ProjectSearchLoader.load(projectId)`** — a separate `@Transactional(readOnly=true)` bean
   (a real proxy call, not a self-invocation, so the transaction actually applies) that loads the
   project's nodes, each node's log files, filter fields, and line pattern fully — force-hydrating
   every lazy collection — before the async fan-out starts, so the long-running scan never touches
   an open Hibernate session from a virtual thread.
2. `SearchServiceImpl.prepare(request)` resolves defaults (`to` = now if absent, `from` = `to` − 1
   day if absent), validates the range (`from` ≤ `to`, span ≤ `search.max-date-range-days`,
   `IllegalArgumentException` otherwise), builds one `LogLineParser` for the whole search
   (`LogLineParserFactory.create`, formatter/regex compiled once) and one combined
   `Predicate<String>` from every non-blank filter value (`buildPredicate` — AND-ed across fields;
   an invalid user-supplied regex value becomes a predicate that matches nothing, not a thrown
   exception) plus free text (case-insensitive substring, `Locale.ROOT`).
3. **`acquireGate()`** — tries the process-wide `Semaphore` (`search.max-concurrent-searches`,
   bean in `SearchConfig`); if saturated, throws `SearchOverloadedException` (→ HTTP 429 for the
   API, an inline banner for the UI) instead of queuing.
4. **Per (node, log file) scan** (`scanLogFile`, one virtual thread each, bounded I/O concurrency
   via a per-search `Semaphore(search.max-nodes-parallel)`):
   - Re-checks reachability of both the live and backup paths right before scanning
     (`PathAvailabilityChecker`) — a stale/never-checked `LogFile.lastCheckStatus` is not trusted.
     Unreachable → recorded in `unreachableNodes` (labeled `"<node> · <output>"`, or just
     `"<node>"` if the output has no label) and skipped, never fails the whole search.
   - **`DatePruner.plan(...)`** expands `{date}` per calendar day in the requested range (assumes
     `yyyy-MM-dd` folder format — documented, not configurable per-project) and turns
     `{HH}`/`{i}`/anything else into `*` globs, all without touching the filesystem; also decides
     whether the live file should be read at all (only if the range reaches into today or later,
     via an injectable `Clock` for testability).
   - **`GlobFileResolver.resolve(baseDir, glob)`** walks the glob one path segment at a time via
     `Files.newDirectoryStream` — portable across Windows/Unix, unlike a whole-path `glob:`
     `PathMatcher`.
   - Files are ordered **backups oldest→newest (by path string), then the live file last**, so
     results come out already-chronological per scan unit.
   - **`LogSourceReaderFactory.readerFor(path)`** picks `PlainLogSourceReader` (plain `.log`) or
     `GzipLogSourceReader` (`.gz`) by filename; both stream lines lazily via `BufferedReader`,
     never buffering a whole file.
   - **`LogEntryAssembler`** groups raw lines into log *entries*: a line where
     `LogLineParser.timestamp(line)` succeeds starts a new entry; every following line without a
     detected timestamp is folded in as a continuation (a stack trace, a wrapped message,
     pretty-printed JSON) — driven entirely by the project's own line pattern, not a hardcoded
     heuristic. Capped at `search.max-continuation-lines`: once hit, the entry is force-flushed and
     further lines start a new, timestamp-less entry, so a project whose pattern matches nothing
     can't buffer an entire file into one unbounded string.
   - **Fast-reject**: an entry outside `[from, to]` is dropped without ever running the field/text
     matcher over its (possibly multi-line) body.
   - **`FieldMatcher`** (one Spring bean per `MatchType`, selected via an injected
     `Map<MatchType, FieldMatcher>`): `ExactTokenMatcher` (whole-token match bounded by
     non-identifier characters, so `tid=abc` doesn't match `tid=abcd`), `SubstringMatcher`
     (case-sensitive `contains`), `RegexMatcher` (user regex, compiled with `Pattern.DOTALL` so
     `.` can span a multi-line entry's embedded newlines). Adding a new `MatchType` means adding a
     new `@Component` implementing `FieldMatcher` — no existing matcher or the selection code
     changes (open/closed).
   - Matches are pushed into that scan unit's own **`NodeProducer`** — a small
     (`search.chunk-size`-sized) bounded blocking queue. `offer()` parks the scanning virtual
     thread when full (backpressure — memory stays bounded regardless of total match count);
     `finish()` (always called, in a `finally`) enqueues a poison-pill sentinel so the consumer
     side knows this producer is done.
5. **`StreamingResultMerger.merge(producers, maxResults, onResult, onTruncated)`** — a bounded
   k-way merge: a `PriorityQueue` holding at most one peeked item per producer, ordered by
   timestamp (nulls sort last via `LocalDateTime.MAX`). Repeatedly polls the earliest item, emits
   it, and refills from that same producer — never materializes the whole result set. This runs on
   the **calling thread** (the one that invoked `search()`/`searchStreaming()`), driving the
   producer virtual threads via their queues. Once `maxResults` is emitted, `onTruncated` fires and
   the merge stops immediately; remaining producer `Future`s are then `cancel(true)`'d (interrupting
   any of them parked mid-`offer`) rather than left to run to completion.
6. Two terminal consumption modes share steps 1–5 (`SearchServiceImpl.runSearch`, parameterized by
   an `onResult` consumer):
   - **`search()`** — buffers every merged line into a `List`, paginates it
     (`page`/`pageSize`, `pageSize <= 0` means "return everything up to the cap"), returns a
     `SearchResult(lines, totalMatched, truncated, unreachableNodes, elapsedMillis)`.
   - **`searchStreaming()`** — buffers into `search.chunk-size`-sized chunks and invokes `onChunk`
     per chunk (used by `/api/search/stream`'s SSE `chunk` events and `/api/search/export`'s
     line-by-line `println`), `onProgress` once per finished scan unit
     (`SearchProgress(label, completedCount, totalCount)`, used by SSE `progress` events), and
     `onComplete` once at the end with a `SearchSummary` (used by SSE's `done` event).

### 8.1 Search tuning (`config.search.SearchProperties`, bound from `search.*`)

| Property | Default | Meaning |
|---|---|---|
| `search.max-results` | 5000 | Hard cap on matched entries; scan stops (truncated) once hit |
| `search.max-nodes-parallel` | 16 | Concurrent (node, log file) scans per search (I/O concurrency) |
| `search.max-continuation-lines` | 300 | Continuation lines folded into one entry before force-flush |
| `search.max-date-range-days` | 30 | Largest allowed `from`–`to` span |
| `search.max-concurrent-searches` | 8 | Global cap on searches running at once (across all users) |
| `search.chunk-size` | 200 | SSE `chunk` event batch size / each `NodeProducer`'s queue capacity |
| `search.sse-timeout-millis` | 60000 | How long an SSE connection may stay open before being forced closed |

Override via env vars (`SEARCH_MAX_RESULTS`, etc., standard Spring relaxed binding) or a mounted
`application.yml`.

## 9. Other backend services

### 9.1 `ProjectService` / `ProjectServiceImpl`

- `saveFromWizard(ProjectWizardForm)` — **replaces the node and field collections wholesale**:
  `project.getLogSources().clear()` / `getFilterFields().clear()` then rebuilds from the form,
  relying on `orphanRemoval = true` to delete the old rows. Blank node/log-file/field rows (every
  field empty) are silently skipped rather than persisted. The `LinePattern` is set to `null`
  (deleting the existing one via orphan removal) if the form's line-pattern step has no content at
  all (`LinePatternForm.hasAnyContent()`).
- `loadForEdit(UUID)` — the inverse mapping, `Project` entity → `ProjectWizardForm`, used by both
  the wizard's edit entry point and `ProjectApiController.getProject`/`updateProject`'s response
  shaping. Note: `sampleLine` is a wizard-only scratch field, never persisted — re-opening a saved
  project for edit shows the derived patterns but an empty sample-line textarea.
- `checkPaths(livePath, backupPath, logFileId)` — checks whichever of the two paths is non-blank,
  joins their messages, and (if `logFileId` is non-null) persists the combined result onto that
  `LogFile` via `recordLogFileCheck`. Used by both the wizard's "Test paths" button and
  `ProjectApiController`'s `/path-check`.

### 9.2 `PathAvailabilityCheckerImpl`

Resolves the path via `Paths.get(...)` (catches `InvalidPathException`), then: doesn't exist →
unreachable; is a directory → reachable, message includes the entry count (`Files.list` count); is
a regular file → reachable, message includes size in bytes; anything else (neither) → unreachable.
IO/security errors while listing are caught and reported as unreachable, never thrown.

### 9.3 `LogLineParserFactory` / `DefaultLogLineParser`

Builds one parser per search with its `DateTimeFormatter`(s) and level `Pattern` **compiled once**,
reused for every line. If a project has no `LinePattern.timestampPattern` configured, falls back to
a fixed list of common formats (`yyyy-MM-dd HH:mm:ss.SSS`, with `,`/`.`/`T` variants). Level
detection falls back to a keyword regex (`TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL`) if no custom
level pattern is set.

Timestamp parsing uses `DateTimeFormatter#parseUnresolved` from index 0 — never throws, so the
fast-reject stays cheap even across the many lines it must skip. **Gotcha:** `parseUnresolved`
skips era resolution, so a `"yyyy"` pattern token resolves to `ChronoField.YEAR_OF_ERA`, not
`YEAR` (`"uuuu"` would give `YEAR`) — reading only `ChronoField.YEAR` silently fails for the
overwhelmingly common `yyyy-MM-dd` pattern. `DefaultLogLineParser.toLocalDateTime` checks `YEAR`
then falls back to `YEAR_OF_ERA`.

### 9.4 `SampleLineAnalyzerImpl`

Heuristic, regex-based, no ML: finds a `yyyy-MM-dd(T| )HH:mm:ss[.,]fraction?` timestamp anywhere in
the line, then searches *after* it for a level keyword, then searches *after* the level for a
dotted logger-name-shaped token (`\b[a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)+\b`). Builds
non-overlapping, priority-ordered (timestamp > level > logger) `HighlightSegment`s covering the
whole line for the wizard's color-coded preview. Always returns the same default level pattern the
search engine itself falls back to, so the suggestion is never worse than the built-in default.

### 9.5 `LogbackXmlParserImpl`

Parses an uploaded `logback-spring.xml` with a hardened `DocumentBuilderFactory` (DTDs disallowed
entirely, external entities disabled — the OWASP-recommended XXE defense; a `logback-spring.xml`
never legitimately needs a DTD). Extracts:
- **MDC fields**: every `%X{key}` (or `%X{key:-default}`, default ignored) found inside any
  `<pattern>` element, first-seen order preserved, each also capturing a `linePrefix` if the
  literal text immediately before the token looks like `word=` (e.g. `tid=%X{traceId}` →
  `linePrefix = "tid="`).
- **Backup pattern**: the first `<fileNamePattern>` element, `${...}` Spring-property placeholders
  resolved from any `<springProperty name=".." defaultValue="..">` declarations, then converted
  token-by-token from Logback's `%d{...}`/`%i` syntax into this app's `{date}`/`{HH}`/`{i}` syntax.
  Everything before the last `/` preceding the first rotation token becomes an informational
  `backupRootHint` (never bound automatically — the real root path is environment-specific).
- **Live path hint**: the first `<file>` element's text, same variable substitution, shown as
  `liveLogPathHint` only.

Throws `LogbackParseException` for an empty upload, unparseable XML, or an unreadable file.

## 10. Frontend architecture

### 10.1 Shared `<head>` fragment and display preferences

[`fragments/head.html`](../src/main/resources/templates/fragments/head.html) is `th:replace`d into
every page's `<head>`. It wires up the Tailwind/HTMX/Alpine CDN scripts, the CSRF meta-tags plus an
`htmx:configRequest` listener that auto-attaches the CSRF header to every HTMX request, and **four
independent, purely client-side (localStorage-only, never a cookie or server round trip) display
preferences**, each applied *pre-paint* via a synchronous IIFE so there's never a flash of the
wrong state:

| Preference | Storage key | States | Applied as |
|---|---|---|---|
| Theme | `theme` | light (default) / dark | `html.dark` class |
| Density | `density` | comfortable (default) / compact | `html.compact` class |
| Skin | `skin` | `console` (default) / `signal` / `default` | `data-skin` attribute |
| Console font | `consoleFont` | `jetbrains` (default) / `system` | `data-console-font` attribute (Console skin only) |

Every skin defines its own light *and* dark set of CSS custom properties (`--lvl-error-fg`,
`--tag-bg`, `--banner-*-bg`, plus skin-specific ones like `--console-accent`/`--signal-line`);
shared component rules (log-level pills, chips, banners, the stream/group-by views) are written
**once** and colored entirely through those tokens — adding a new skin means defining a token set
plus whatever structural CSS it wants, never duplicating every component rule. "Default" reproduces
today's original fixed Tailwind red/amber/blue/slate palette so it looks identical whether or not a
skin has ever been chosen.

Every HTMX-swapped region gets the same fade/slide-in entrance animation
(`htmx:afterSwap` → `.fade-in-up` class, removed on `animationend`) with zero per-template wiring.

### 10.2 The search page (`search.html`) — client-side behavior

The server renders one HTMX fragment (`resultsFragment`) per search/page-change; **everything else
— view mode, grouping, highlighting, quick filters, filter chips, stats, panel collapse — is
rebuilt entirely client-side** in a `<script>` block, because the controller (`SearchController`)
doesn't echo request parameters back into the model.

- **`onResultsSwapped()`** is the single entry point, called on `htmx:afterSettle` (not
  `afterSwap` — instrumentation showed htmx's own settle step reverts class changes made during
  `afterSwap` within ~50ms, silently undoing view-mode hide/show; `afterSettle` fires after
  settling completes, so it's the last word). It calls, in order: `processLogCards()`,
  `renderStatsStrip()`, `renderFilterChips()`, `captureEntries()`, `refreshDerivedView()`.
- **`processLogCards()`** does two related things per `#cards-view .log-card`, in one pass over
  each card's `.log-line-text`, because both need the same leading-timestamp match:
  - **Local time.** `LogLine.timestamp()` is a wall-clock `LocalDateTime` with no zone attached
    (see §7 / `DefaultLogLineParser` — it parses the digits but deliberately discards any
    offset/zone the line prints, since date-range search compares wall-clock values). The server
    renders that value, **including milliseconds** (`yyyy-MM-dd'T'HH:mm:ss.SSS` — precision the
    conversion below needs and must not drop), pre-formatted for a no-JS fallback, and also
    verbatim as `data-ts-raw` on `.log-timestamp`. The raw digits are **always treated as UTC**
    (`new Date(dataTsRaw + 'Z')`, converted to the browser's local time and re-formatted with
    `formatLocalTimestamp()`, which also carries milliseconds through) — including on a line that
    prints its own zone/offset suffix (`+0530`, `Z`, …). That printed offset is deliberately
    ignored for the conversion itself: an earlier version honored it when present, but real
    projects turned up log lines whose offset suffix doesn't actually describe the zone the digits
    were written in (a static/misconfigured `%z`-style token), so trusting it selectively produced
    *worse* results (silently no-op'd conversions) than uniformly assuming UTC. `LEADING_TIMESTAMP_RE`
    still matches that trailing zone/offset (captured in group 1) — its only remaining use is
    finding how much text to strip for the dedup step below; group 1 is not read for the time math.
    The offset text itself is never displayed anywhere (an earlier version surfaced it in a `title`
    tooltip — removed per product feedback: it read as a confusing "why is there a stray +0530
    here" artifact, not a helpful reference).
  - **Prefix dedup.** `LogLine.raw()` is the full original line, which already starts with the same
    timestamp/level text the badges render separately, so left unmodified every card duplicated its
    own date and level. This strips the matched leading timestamp (+ offset, if any) from the first
    physical line, then separately removes this card's own extracted level *value* (read from the
    `.log-level` badge text, not a hardcoded keyword list — a project can configure any vocabulary)
    wherever it first appears on that first line, mirroring `DefaultLogLineParser.level()`'s own
    first-match-anywhere search so it removes exactly the substring that produced the badge. Only
    ever touches the **first** line of a (possibly multi-line, stack-trace-bearing) entry — a
    continuation line is never scanned for a level word, matching `LogEntryAssembler`, which also
    only ever calls `parser.level(...)` on an entry's first line. Only applied when this card
    actually has a parsed timestamp (`data-ts-raw` present) / level (badge text isn't `'—'`) to
    match against — a line the backend couldn't parse is left completely untouched. Best-effort by
    construction: a project with a custom, non-standard `timestampPattern` (e.g. `dd/MM/yyyy`) simply
    won't match `LEADING_TIMESTAMP_RE` and keeps showing its full raw prefix — same class of
    limitation already documented for `highlightResults()`'s `REGEX`-field best-effort matching.
- **`captureEntries()`** snapshots the server-rendered `#cards-view .log-card` elements into a
  plain-JS array `currentEntries` (`cardEl`, `level`, `node`, `file`, `timestamp`, `raw`) — the
  single canonical source every other client-side feature reads from. `timestamp`/`raw` are read
  *after* `processLogCards()` has already localized/deduped the DOM text.
- **View mode** (`currentViewMode()`/`setViewMode()`, `localStorage['viewMode']`, default
  `"stream"`): Cards (the server-rendered `#cards-view`) vs. Stream (`#stream-view`, entirely
  client-built by `buildStreamRow()` — a dense, ellipsis-truncated single-line-per-entry layout
  with an expand toggle for multi-line/long entries). The expand toggle is always the row's
  *first* child, in a fixed-width `.log-row-toggle-slot` gutter (empty/reserved even on rows with
  nothing to expand, so every row's timestamp still lines up at the same x position) — not the
  last child. On a narrow viewport, or with long node/file labels, the row's fixed-width badges can
  collectively overflow it even though `.log-line-text` itself shrinks/ellipsizes correctly
  (`flex:1; min-width:0`); since horizontal scroll always starts at the row's beginning, a toggle
  parked at the *end* of an overflowing row scrolls out of reach and becomes unclickable without
  scrolling right first — this is why it lives at the start instead.
- **Group by** (`applyGrouping()`): clusters `currentEntries` under a heading per distinct value of
  a chosen filter-field key, extracted from the raw line via a regex built from
  `key\s*[=:]\s*([^\s,)&\]]+)`; entries where the key can't be extracted land in a trailing
  "Ungrouped" section. Always rebuilt from the untouched `currentEntries` snapshot, so switching
  back to "None" is just "don't group" on the next pass, never an undo.
- **Highlighting** (`highlightResults()`): wraps every occurrence of any non-blank search-form
  input value (free text + every `filter_*` field, longest-first so overlaps prefer the longest
  match) in `<mark>`, across whichever view is currently visible. Best-effort only for `REGEX`
  fields, since the public page has no visibility into a field's `MatchType`.
- **Stats strip + quick filters** (`renderStatsStrip()`/`applyQuickFilters()`): counts by
  level/node/file **among the entries on this page only** (not the full, possibly-truncated match
  set), rendered as clickable pills; clicking toggles a pill `.active` and re-filters
  `currentEntries` by show/hide by `style.display` — pills within one category OR together,
  categories AND together.
- **Filter chips** (`renderFilterChips()`): dismissible summary pills read straight from the live
  form inputs; clicking a chip's `×` clears that one input and re-triggers the form's own
  `hx-get` via `htmx.trigger(form, 'submit')`.
- **Download all matches** (`downloadResults()`): navigates to `/api/search/export` with the
  current form's `FormData` as query params — this is the one place the SSE-adjacent streaming API
  is actually used by the built-in UI (see §7.5's note).
- **Panel collapse** (`toggleSearchPanel()`, `localStorage['searchPanelCollapsed']`): hides/shows
  the project-picker + search-form chrome once results are showing, independent of any search.
- **`#search-panel` layout**: the project-picker and `#search-form` are two separate `<form>`s (the
  picker is a plain GET navigation on `<select>` change; `#search-form` is the htmx-driven one) but
  render as a single visual row — each form is `class="contents"` (`display: contents`), so its
  children become direct flex items of the shared `.search-toolbar` wrapper around both, which
  wraps (`flex-wrap`) rather than stacking every field on its own labeled line the way it used to.
  Because `display: contents` boxes can't take a background/border themselves, the skins' card
  styling (`html[data-skin=...] .search-toolbar` in head.html) targets that outer wrapper, not
  `#search-form` — don't reintroduce a `.project-picker`/`#search-form` background selector pair,
  it would silently no-op on the now-boxless forms.
- Pagination: Prev/Next buttons are `hx-get="/search" hx-include="#search-form"` with
  `hx-vals="js:{page: ...}"`; the page-jump input calls `htmx.ajax(...)` directly with a merged
  `FormData` + explicit page.

### 10.3 The admin wizard's frontend

Each wizard step is a plain HTML `<form>` whose buttons are individually `hx-post`-wired (not a
single form-level submit) so "Add node"/"Remove"/"Test paths"/"Analyze"/"Back"/"Next" can each hit
a different endpoint while still submitting the whole form's current field values
(`hx-include="closest form"`). The step template itself is chosen server-side
(`admin/projects/steps.html`'s `th:switch` on `currentStep.name()`) — see the Thymeleaf gotcha in
§11. The "Test paths" button reads live (unsaved) path input values by DOM id since their `name`
attributes are indexed list-binding paths, not plain field names (§7.4).

## 11. Known gotchas — read before touching these areas

**Spring Session only persists attributes you explicitly re-`setAttribute`.** Mutating an object
retrieved via `session.getAttribute(...)` in place does **not** get written back to the JDBC
session store — the next request reloads the stale, unmutated version from the DB. This is why
`ProjectWizardController`'s private `step(...)` helper always calls
`session.setAttribute(DRAFT_KEY, draft)` before returning, even though `draft` was already mutated
by reference. **If you add a new session-backed mutable flow, follow this pattern.** MockMvc built
with `webAppContextSetup(...).apply(springSecurity())` does *not* run the real session filter, so
it holds the same object reference across "requests" and will pass even if this bug is present —
verify session-backed flows with real `curl` + a cookie jar, not just MockMvc.

**Thymeleaf: never put `th:insert`/`th:replace` on the same tag as `th:switch`/`th:case`/`th:if`/
`th:with`.** Fragment-inclusion attributes have higher processing precedence, so they execute
*before* the conditional gets a chance to prune non-matching branches — every branch's fragment
gets inserted unconditionally. This produced a real bug in `steps.html`'s step switch (every wizard
step rendered *all* steps' forms stacked together). Fix, already applied and must be preserved:
always nest — conditional on an outer tag, `th:replace` on an inner child:
```html
<div th:case="'X'"><div th:replace="~{... :: frag}"></div></div>
```
When testing a step/state-branching fragment, assert the *absence* of sibling branches' content,
not just the presence of the expected content — a plain `containsString` won't catch this class of
bug (the real content is genuinely there, just with extra content alongside it).

**`DateTimeFormatter.parseUnresolved` needs a `YEAR_OF_ERA` fallback.** See §9.3.

**`flyway-database-h2` is not a real Maven artifact.** H2 support ships inside `flyway-core`
itself. See §2.1.

**Boot 4's modularized auto-config bites silently.** See §2.1 — no exception, no warning, the
integration just doesn't activate.

**The global `.hidden { display: none !important; }` rule (head.html) beats Tailwind's `dark:`/
state variants on the same element.** `!important` always wins over a plain-specificity utility
like `dark:block`, regardless of source order — so an element that starts `class="hidden ... 
dark:block"` (base-hidden, shown only in one state) stays hidden forever, since `dark:block`'s
`display:block` can never outrank it. This is exactly what made the theme toggle's moon icon
invisible in dark mode (light *and* dark icon both hidden, empty-looking button). Fix pattern:
give the state-only icon/element its own dedicated class instead of literal `hidden`, e.g.
`.theme-icon-moon { display: none; } html.dark .theme-icon-moon { display: block; }` (mirrors the
pre-existing `.density-icon-compact` pattern). `.hidden` itself must stay `!important` — it's relied
on elsewhere (`toggleSearchPanel()`, `#quick-filter-empty`, `#stream-view`) to reliably win over
whatever other classes an element happens to carry.

**MockMvc's `webAppContextSetup` doesn't run the real Spring Session filter chain** — see the
session-mutation gotcha above; this is why the original build's verification discipline (below)
mattered.

### Verification discipline this codebase was built with

Every phase of this build was verified by (1) unit/integration tests **and** (2) booting the real
app and driving it with `curl` (login flow, wizard steps, multipart upload, search queries) against
real files on disk, reading the raw HTML response rather than just asserting `containsString(...)`
in MockMvc. This caught two real bugs a looser test would have missed — the session-mutation gotcha
and the Thymeleaf fragment-precedence gotcha above, both invisible to MockMvc/`containsString`.
When changing wizard/search rendering logic, re-run this kind of check, not just the existing
tests.

## 12. Database & migrations

Flyway locations: `classpath:db/migration/common,classpath:db/migration/{vendor}`
(`application.yml`). `common/` is the portable baseline (every SQL database); `{vendor}` resolves to
whichever engine is actually running — `postgresql/` (prod) and `h2/` (dev) both exist as vendor
overrides.

| Migration | Contents |
|---|---|
| `common/V1__init.sql` | `project`, `log_source`, `filter_field`, `line_pattern` tables |
| `common/V2__indexes.sql` | Supporting indexes |
| `postgresql/V3__spring_session.sql` | Spring Session JDBC tables, copied verbatim from `spring-session-jdbc`'s `schema-postgresql.sql`. Prod: Flyway owns these (`spring.session.jdbc.initialize-schema: never`). Dev: Spring Session auto-creates them on the embedded H2 DB instead (`initialize-schema: embedded`) — **don't let both try to create the tables at once.** |
| `common/V4__log_file.sql` | Adds `log_file` table (the node → per-output split, §5); migrates each existing `log_source`'s single live/backup/pattern config into a `log_file` row labeled `"Application"`, reusing the `log_source` row's own id as the new `log_file` id (safe — different table's PK space); then drops those columns from `log_source`. |
| `h2/V5__dev_seed_project.sql` | Dev-only convenience data: since it lives under the `h2` vendor location it only ever runs against the in-memory H2 dev database, never prod. Seeds one ready-to-search project ("360 API": one node/log-file, a `tid`/"Trace ID" `EXACT_TOKEN` filter field, and a `LinePattern` derived the same way the wizard's "analyze sample line" step would) so a fresh `mvnw spring-boot:run` has something to search immediately. |

**To add support for another database** (MySQL, Oracle, SQL Server): add its JDBC driver +
`flyway-database-<db>` module to `pom.xml`, point `spring.datasource.*` at it, add
`db/migration/<db>/Vn__*.sql` for anything vendor-specific, and if Spring Session needs its tables
via Flyway there too, copy the matching `schema-<db>.sql` from `spring-session-jdbc` into
`db/migration/<db>/`. **Known gap:** SQL Server's `TIMESTAMP` type means "rowversion," not a
date/time column — a SQL Server target needs `db/migration/sqlserver/` using `DATETIME2` instead.

## 13. Configuration reference

| Property | Where | Default | Purpose |
|---|---|---|---|
| `loguty.admin.username` / `.password` | `AdminProperties` | none (fails fast if blank) | The single admin account; `LOGUTY_ADMIN_USERNAME`/`_PASSWORD` env vars in prod, `admin`/`admin` in dev |
| `loguty.api.cors.allowed-origins` | `CorsConfig` | empty (no CORS) | Comma-separated allowed origins for `/api/**` |
| `search.max-results` | `SearchProperties` | 5000 | See §8.1 |
| `search.max-nodes-parallel` | `SearchProperties` | 16 | See §8.1 |
| `search.max-continuation-lines` | `SearchProperties` | 300 | See §8.1 |
| `search.max-date-range-days` | `SearchProperties` | 30 | See §8.1 |
| `search.max-concurrent-searches` | `SearchProperties` | 8 | See §8.1 |
| `search.chunk-size` | `SearchProperties` | 200 | See §8.1 |
| `search.sse-timeout-millis` | `SearchProperties` | 60000 | See §8.1 |
| `JDBC_DATABASE_URL` / `_USERNAME` / `_PASSWORD` | `application-prod.yml` | localhost Postgres | Prod datasource |
| `SPRING_PROFILES_ACTIVE` | Spring Boot | `dev` | Must be `prod` in production |
| `management.endpoints.web.exposure.include` | `application.yml` | `health,info` | Actuator endpoints exposed |

## 14. Current state / what's *not* here

- No multi-admin / role system — exactly one admin account, by design.
- No automatic vendor override beyond PostgreSQL — MySQL/Oracle/SQL Server each need their own
  `db/migration/<vendor>/` folder and a Flyway/driver dependency added (§12).
- `LinePattern.loggerPattern`/`timestampRegexOrPosition` are captured and persisted but **not
  currently consumed** by the search engine (`LogLineParserFactory` only reads `timestampPattern`
  and `levelPattern`) — they exist for the wizard's confirm/correct UI and potential future use.
- The built-in search UI does not consume the SSE `/api/search/stream` endpoint — see the note in
  §7.5. If that changes, update this section and §10.2.
- No CI/CD configuration, no Docker/containerization files.
