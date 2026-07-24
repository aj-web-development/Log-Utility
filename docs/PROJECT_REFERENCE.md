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

Last verified against the code: 2026-07-24.

---

## 1. What this is

A standalone Spring Boot app for searching another application's log files. An admin configures a
target application's logs once (upload its `logback-spring.xml` or use the guided setup wizard),
and afterwards anyone can search that project's live and rotated/gzip-compressed logs — across
multiple nodes, each of which may write more than one distinct log output (e.g. `app.log`,
`error.log`, `access.log`) — filtered by date range, by a configured identifier field (trace id,
session id, ...), or by free text. Search requires no login. Only project configuration sits
behind a single admin account.

The backend exposes one JSON REST API (`/api/**`), including a Server-Sent-Events streaming search
endpoint and a plain-text export endpoint, documented via springdoc-openapi (`/swagger-ui.html`).
The UI is a React single-page app (`frontend/`) that consumes that same API — there is no
server-rendered page anymore; the Spring Boot app's only non-API HTTP responsibility is serving the
SPA's static build and forwarding its client-side routes to `index.html`.

## 2. Tech stack & versions

- **Java 21** (floor, not ceiling — virtual threads and other 21-only APIs are used freely).
- **Spring Boot 4.1.0** → Spring Framework 7, Jakarta EE 11, Spring Security 7, Hibernate ORM 7.
  Not the Boot 3.x line most tutorials/training data assume.
- **Maven** (`./mvnw` / `mvnw.cmd`). GroupId `com.app`, artifactId `logutility`, base package
  `com.app.logutility`.
- **springdoc-openapi-starter-webmvc-ui** (3.0.3) — auto-generates `/v3/api-docs` and
  `/swagger-ui.html` from the REST controllers' `@Operation`/`@Tag` annotations.
- **PostgreSQL** (prod) / **H2** (dev, `MODE=PostgreSQL`), via **Flyway**.
- **`frontend/`**: React 19, TanStack Router (client-only, file-based routes, no SSR) + TanStack
  Query, shadcn/Radix components, Tailwind v4, built with Vite. Built by Maven
  (`com.github.eirslett:frontend-maven-plugin`, downloads its own pinned Node — not required on
  `PATH`) straight into `src/main/resources/static`, so `mvnw clean package` still produces one
  self-contained jar/war.
- Lombok (`@Getter`/`@Setter`/`@RequiredArgsConstructor`) on entities, form-backing objects, and
  services. **DTOs crossing an HTTP boundary are Java `record`s**, never Lombok classes.

### 2.1 Spring Boot 4's modularized auto-configuration — read this before adding any dependency

In Spring Boot 4, auto-configuration for non-core integrations moved out of the starter jars into
separate `spring-boot-<tech>` modules. **Adding a library's raw jar to the classpath is often not
enough to activate it** — no exception, no warning, it just silently does nothing. This has already
happened in this codebase:

| Integration | Symptom when the module was missing | Fix |
|---|---|---|
| Flyway | Zero Flyway log lines at boot; no tables created; app "boots successfully" anyway | add `org.springframework.boot:spring-boot-flyway` |
| H2 web console | `/h2-console` → 404 even with `spring.h2.console.enabled=true` | add `org.springframework.boot:spring-boot-h2console` |
| MockMvc test support | `@AutoConfigureMockMvc`/`@WebMvcTest` unresolvable from `spring-boot-starter-test` | moved to `spring-boot-webmvc-test` — this repo sidesteps it entirely by building `MockMvc` manually: `MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build()` in every controller test's `@BeforeEach` |

(Spring Session JDBC used to be a third example here — it's been removed entirely along with the
Thymeleaf UI that needed it; the app is fully stateless now, see §6.)

**Rule of thumb:** when adding any new integration that isn't a `spring-boot-starter-*`, check the
`spring-boot-dependencies` BOM for a matching `spring-boot-<tech>` artifact, add it, and verify the
integration actually activates (grep the boot log, or curl the endpoint) — never assume. Also:
`flyway-database-h2` **does not exist** as a Maven artifact — H2 support ships inside
`flyway-core` itself; only non-default engines (`flyway-database-postgresql`, etc.) are separate.

## 3. Build, run, test

```bash
mvnw.cmd spring-boot:run              # dev profile (H2), default — http://localhost:8080
mvnw.cmd clean package                # builds frontend/ (npm ci && npm run build), then the executable jar → target/logutility-0.0.1-SNAPSHOT.jar
mvnw.cmd clean package -Pwar21        # WAR for an external servlet container → target/logutility-java21.war
mvnw.cmd test                         # full suite (also builds frontend/ first - see the frontend-maven-plugin's generate-resources binding in pom.xml)
mvnw.cmd test -Dtest=ClassName        # single test class
mvnw.cmd test -Dtest=ClassName#method # single test method
```

```bash
cd frontend && npm run dev            # Vite dev server (port 5173), proxies /api to localhost:8080 - run alongside `mvnw spring-boot:run`
cd frontend && npm run build          # what the Maven build calls; writes straight into ../src/main/resources/static
```

- `war21` requires a JDK 21 entry in `~/.m2/toolchains.xml` (uses `maven-toolchains-plugin`); WAR
  packaging marks embedded Tomcat `provided` since the external container supplies it.
- Dev admin login: `admin` / `admin` (from `application-dev.yml`, dev-profile only, throwaway) —
  the React login page verifies it by sending it as HTTP Basic against `GET /api/projects` (there's
  no separate JSON login endpoint; see §6 and §10).
- Prod requires `SPRING_PROFILES_ACTIVE=prod` + `LOGUTY_ADMIN_USERNAME`/`LOGUTY_ADMIN_PASSWORD`
  (app **fails fast at startup** without them, `SecurityConfig#userDetailsService`) +
  `JDBC_DATABASE_URL`/`_USERNAME`/`_PASSWORD`.
- No CI/CD config and no Docker/containerization files exist in this repo.
- The `frontend-maven-plugin`'s Node download can be flaky on some machines (antivirus/real-time
  scanning intercepting the freshly-extracted `node.exe`, or a corrupted npm cache download) — if
  `install-node-and-npm` or `npm ci` fails, delete `frontend/node` (and, if `npm ci` itself reports
  `ENOTEMPTY`/a corrupted-tarball warning, `frontend/node_modules` + `frontend/package-lock.json`
  too) and re-run; it has reliably succeeded on retry every time this has come up so far.

Most tests are fast unit tests with no Spring context (search pipeline, Logback XML parser,
sample-line analyzer). `*IntegrationTest` classes boot the full app against the H2 dev profile.
`config.security.SecurityIntegrationTest` covers the one security chain, including that
`/admin/**` now serves the public SPA shell (no more session redirect) while `/api/projects/**`
still bare-401s an anonymous request.

## 4. Package map — layer first, then domain

`com.app.logutility` is organized **by technical layer, then by domain** — e.g. `controller.project`,
`entity.project`, `service.project`, and `repository.project` are four separate top-level packages,
not one `project` package. The domain name is always the *second* path segment.

```
com.app.logutility
├─ LogutilityApplication        extends SpringBootServletInitializer (WAR-deployable + runnable jar)
├─ config
│   ├─ security   AdminProperties, SecurityConfig (1 stateless filter chain), CorsConfig, DevH2ConsoleSecurityConfig
│   ├─ search     SearchProperties (search.* tuning), SearchConfig (Clock + Semaphore beans)
│   └─ openapi    OpenApiConfig (springdoc metadata + basic-auth security scheme)
├─ controller
│   ├─ web        SpaController (forwards /, /login, /admin/** to the SPA's index.html)
│   ├─ project    ProjectApiController (REST CRUD + wizard-helper endpoints)
│   ├─ search     SearchApiController (REST search), SearchStreamController (SSE + plain-text export)
│   └─ common     ApiExceptionHandler (@RestControllerAdvice for the 3 REST controllers above),
│                  ApiErrorAttributes (reshapes Boot's default /error body for unmatched /api/** routes)
├─ entity.project      Project, LogSource, LogFile, FilterField, LinePattern, MatchType, CheckStatus
├─ repository.project  ProjectRepository, LogSourceRepository, LogFileRepository,
│                       FilterFieldRepository, LinePatternRepository
├─ request
│   ├─ project    ProjectWizardForm, NodeForm, LogFileForm, FilterFieldForm, LinePatternForm (plain
│   │              in-memory validation/persistence adapter objects now - ProjectApiController builds
│   │              one fresh per create/update request, nothing session-held anymore) +
│   │              ProjectRequest, NodeRequest, LogFileRequest, FilterFieldRequest, LinePatternRequest,
│   │              PathCheckRequest (REST request records)
│   ├─ parser     SampleLineRequest
│   └─ search     SearchRequest
├─ response
│   ├─ project    ProjectDetailResponse, NodeResponse, LogFileResponse, FilterFieldResponse,
│   │              LinePatternResponse, PathCheckOutcome, ProjectSummaryDto, PublicProjectView,
│   │              PublicFilterFieldView
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

## 6. One stateless security chain

`SecurityConfig` used to define two independent `SecurityFilterChain` beans (one for the JSON API,
one for a server-rendered Thymeleaf/HTMX UI with form login and Spring Session JDBC). That
Thymeleaf UI is gone, replaced by the React SPA in `frontend/`, and an SPA has nothing left for a
*server-side* session to gate — the shell has to load for anyone so its own client-side JS can
decide what to render. So `SecurityConfig` now defines **one** stateless `SecurityFilterChain`:

- `/api/projects/**` → `authenticated()`, via HTTP Basic (`httpBasic(Customizer.withDefaults())`).
- Everything else — `/api/search/**`, static SPA assets, and the client-routed SPA paths
  `SpaController` forwards (`/`, `/login`, `/admin/**`) — → `permitAll()`.
- `SessionCreationPolicy.STATELESS`, CSRF disabled throughout (nothing here relies on a cookie the
  browser attaches automatically, so CSRF protection doesn't apply to any of it).

**`DevH2ConsoleSecurityConfig`** (`@Order(1)`, `@Profile("dev")`) is unchanged: a separate,
higher-priority chain matching only `/h2-console/**`, permitting all access and relaxing
CSRF/frame-options so the console (which renders in an iframe) works. Never active outside the dev
profile.

The real access-control boundary is entirely at the API layer now: `/admin/**` page loads succeed
for anyone (they just render the SPA shell), but every write — and every read of a project's full
configuration — still requires a valid `Authorization: Basic` header on the actual `/api/projects/**`
call. The frontend's own route guard (`frontend/src/routes/_shell.admin.tsx`'s `beforeLoad`,
checking whether credentials are in `sessionStorage`) is a UX nicety — it stops an unauthenticated
visitor from *seeing* the admin pages flash before a 401 would occur, it isn't the security
boundary itself.

The one in-memory `UserDetailsService` (`SecurityConfig`), built from `AdminProperties`
(`loguty.admin.username`/`.password`, env vars `LOGUTY_ADMIN_USERNAME`/`LOGUTY_ADMIN_PASSWORD` in
prod), BCrypt-encoded once at startup, throws `IllegalStateException` at bean-creation time if
either is blank — a production instance can never come up with an unauthenticated or blank admin
account. This is also, indirectly, what the frontend's login page authenticates against — see §10:
there's no separate JSON login endpoint, signing in *is* a successful `GET /api/projects` call with
a freshly-built Basic header.

**CORS**: off by default. `CorsConfig` registers a `CorsConfigurationSource` for `/api/**` only,
driven by the (empty-by-default) property `loguty.api.cors.allowed-origins` (comma-separated). The
bundled SPA never needs this (same-origin); it's there for any other external API consumer.

**OpenAPI**: `OpenApiConfig` declares one global `basicAuth` security scheme so Swagger UI's "Try it
out" can authenticate against `/api/projects/**`; `/api/search/**` endpoints are annotated
`@SecurityRequirements` (empty) to show as public in the generated docs.

## 7. Request flows

### 7.1 Frontend routes → REST API

There's no server-side rendering left to walk through step by step — every `frontend/src/routes/*`
page is a plain React component that calls the REST API in §7.2 via `frontend/src/lib/api.ts`. See
§10 for the frontend's own architecture (routing, auth, the wizard, the search page's client-side
behavior); the short version, route by route:

| Route | Backend calls |
|---|---|
| `/` (search) | `GET /api/search/projects` (picker), `GET /api/search/projects/{id}` (fields), then `POST /api/search` (batch/paginated) or `GET /api/search/stream` (live, via `EventSource`) per search; `GET /api/search/export` is a plain download link |
| `/login` | `GET /api/projects` with a freshly-built `Authorization: Basic` header — success *is* the login, there's no dedicated login endpoint |
| `/admin` | `GET /api/projects` (HTTP Basic) for the dashboard totals |
| `/admin/projects` | `GET /api/projects` (list), `DELETE /api/projects/{id}` |
| `/admin/projects/new`, `/admin/projects/$id/edit` | `GET /api/projects/{id}` (edit prefill only), `POST /api/projects/sample-line/analyze`, `POST /api/projects/path-check` per wizard step, `POST /api/projects` or `PUT /api/projects/{id}` on save |
| `/admin/projects/upload` | `POST /api/projects/logback/parse` (multipart), then hands the parsed result to the `new` route in-memory (`frontend/src/features/wizard/wizardPrefill.ts`) |

`ProjectApiController`'s create/update endpoints are exactly what used to be called "a one-shot
equivalent of the wizard flow" back when there was a separate session-backed multi-step server
flow — now they're just *the* wizard's backend, full stop: one complete `ProjectRequest` payload,
validated with `ProjectWizardValidation`, persisted with `ProjectService.saveFromWizard`.

### 7.2 REST API (`/api/**`)

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

The React search page uses **both** consumption modes, unlike the old Thymeleaf page (which only
ever called the batch path): the default view calls batch `POST /api/search` (paginated), and its
"Live" toggle switches to `GET /api/search/stream` via a native `EventSource` (see §10.3). The
plain-text `/export` endpoint backs the Export link either way.

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
`ApiExceptionHandler`, never leaks a raw exception message for a 5xx), leaving Boot's default
`/error` handling untouched for everything else (the SPA's own routes never hit it in practice,
since `SpaController` forwards them to `index.html` before any error path would).

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

## 10. Frontend architecture (`frontend/`)

A React 19 SPA: TanStack Router (client-only — no server rendering, no TanStack Start/Nitro, even
though the design it was ported from used them, see the note at the end of this section) for
file-based routing, TanStack Query for server-state caching, shadcn/Radix for components, Tailwind
v4 for styling, Vite for the build. `frontend/src/main.tsx` is the entry point:
`createRouter()` + `<QueryClientProvider><AuthProvider><RouterProvider .../></AuthProvider></QueryClientProvider>`.

### 10.1 Routing

File-based, under `frontend/src/routes/`: dot-separated filename segments nest the same way
directories would (`_shell.admin.projects.new.tsx` → `/admin/projects/new`).
`@tanstack/router-plugin`'s Vite plugin generates `frontend/src/routeTree.gen.ts` from this
directory at build/dev time — it's gitignored, never hand-edited.

- `__root.tsx` — just `<Outlet/>` plus the 404/error boundary components. No document shell here
  (that moved to `frontend/index.html` since there's no SSR anymore — see the note below).
- `_shell.tsx` — wraps every route in `AppShell` (sidebar + `TopBar`).
- `_shell.admin.tsx` — parent of every `/admin/*` route; its `beforeLoad` is the **only** place the
  client-side "you must be signed in" redirect lives (`redirect({ to: "/login" })` if
  `getStoredAuth()` is empty). See §6 for why this is UX, not the real security boundary.
- `_shell.index.tsx` (`/`), `login.tsx` (`/login`) sit outside the admin guard — both public.
- `_shell.admin.projects.$id.edit.tsx` — the one route that didn't exist in the original design
  reference; added because the real backend needs an edit flow the mock never had (see §10.4).

### 10.2 Shared infrastructure (`frontend/src/lib/`)

- **`api.ts`** — the single fetch wrapper (`request<T>`) everything else calls through. Attaches
  the stored `Authorization: Basic` header only when the path starts with `/api/projects`; on any
  non-2xx response, parses the body as the backend's `ApiError` shape and throws an
  `ApiRequestError`; on a `401` from a `/api/projects/**` call, clears stored auth and hard-redirects
  to `/login`. Typed endpoint functions (`searchApi.*`, `projectApi.*`) live here too, built on top
  of `request`.
- **`auth.tsx`** (`AuthProvider`/`useAuth`) + **`authStorage.ts`** — there is **no separate JSON
  login endpoint**. `/api/projects/**` is already stateless HTTP Basic (§6), so the login page just
  builds `Authorization: Basic base64(user:pass)` and calls `GET /api/projects` with it directly —
  a `200` *is* a successful login. The header is then kept in `sessionStorage` (cleared on sign-out,
  a `401`, or the tab closing) and reused by `api.ts` for every subsequent admin call.
  `// ponytail:` comment on `authStorage.ts` marks this explicitly as the deliberate simplification
  it is (credentials in `sessionStorage`, fine for a single-admin internal tool; upgrade path is a
  short-lived server-issued token if this ever needs to be exposed beyond a trusted admin).
- **`activeProject.ts`** + **`useActiveProject.ts`** — the SPA's own client-side "active project"
  (`localStorage` key `loguty.activeProjectId`), since the public search API has no server-side
  concept of one (`projectId` is a required field/param on every `/api/search/**` call — see §7.2).
  Sourced from the public `GET /api/search/projects` list (no auth needed), shared by the search
  page's picker and the `TopBar` switcher — there's deliberately only one project switcher control
  in the whole UI, not a separate one per page.
- **`projectStatus.ts`** — the admin dashboard/list pages show a healthy/degraded/silent status
  dot per project, derived from real data: `GET /api/projects` (the summary list) doesn't carry
  per-log-file check results, so `useProjectStatuses(ids)` fires one `GET /api/projects/{id}` per
  visible project in parallel (via `useQueries`) and derives status from
  `LogFileResponse.lastCheckStatus` (any `UNREACHABLE` → degraded, none configured → silent, else
  healthy). N+1 by construction — accepted because this is a small internal admin tool, not a
  public dashboard; don't scale this pattern up to hundreds of projects without adding a real
  aggregate endpoint instead.

### 10.3 The search page (`frontend/src/features/search/SearchScreen.tsx`)

- **Two result modes**, both real backend calls: batch (`POST /api/search`, paginated,
  `PAGE_SIZE = 50`) is the default; a "Live" switch reroutes the same Run action to
  `GET /api/search/stream` via a native `EventSource` (no SSE client library — the endpoint is a
  plain public GET with no custom headers needed), accumulating `chunk` events into state,
  showing `progress` events while running, and finalizing on `done`. Export is a plain
  `<a href="/api/search/export?...">` link, not a fetch/blob download, since the backend already
  sets `Content-Disposition: attachment`.
- **Timestamp handling** (`features/search/logLine.ts`) ports the old Thymeleaf page's exact
  behavior: `LogLine.timestamp` is a wall-clock `LocalDateTime` with no zone (`DefaultLogLineParser`
  parses the digits but discards any printed offset), always treated as UTC and converted to the
  viewer's local time (`new Date(iso + "Z")`) regardless of what offset the raw line prints after
  it — real projects have shown a misconfigured/static offset suffix that doesn't describe the zone
  the digits were actually written in, so honoring it selectively produced worse results than
  uniformly assuming UTC. `summaryLine()` strips that same leading timestamp + the entry's own
  level token from the collapsed card's preview line (mirrors `DefaultLogLineParser.level()`'s
  first-match-anywhere search) — the full `raw` text is always still shown in full once a card is
  expanded.
- **Level filtering is client-side only** (`levelBucket()`/`enabledLevels`) — `SearchRequest` has
  no server-side level parameter; toggling a level chip filters whatever's already been fetched,
  same as `MatchType`-driven field filters are the closest thing to a server concept of it.
  "Quick range" pills (5m/15m/1h/...) are pure client-side convenience that compute and fill in
  explicit `from`/`to` values — there's no relative-range concept on the backend, `SearchRequest`
  only ever takes absolute timestamps.
- **No separate Cards/Stream view-mode toggle or group-by** (both real features of the old
  Thymeleaf page) — the current design has one card-list layout; density (comfortable/compact,
  `localStorage` key `loguty.density`) is the only layout lever ported over. Add view-mode/group-by
  back as new features if a future task asks for them; they weren't dropped for a technical reason,
  just out of scope for the initial port.

### 10.4 The admin wizard (`frontend/src/features/wizard/`)

`ProjectWizard.tsx` renders 6 steps (details → nodes → sample line → line pattern → fields →
review) driven by local React state (`types.ts`'s `WizardState`), reused by both
`/admin/projects/new` (`mode="create"`) and `/admin/projects/$id/edit` (`mode="edit"`, prefilled
via `wizardStateFromDetail(ProjectDetailResponse)`). `wizardStateToRequest()` converts back to a
`ProjectRequest` for the final `POST`/`PUT`. Nodes are nested exactly like the backend model — each
node holds a list of log files, added/removed independently at both levels (`steps/NodesStep.tsx`);
its "Test paths" button calls the real `POST /api/projects/path-check`, not a placeholder. A "line
pattern" step exists as its own step (prefilled from the sample-line step's `analyze` call) since
`LinePatternRequest` is real, required data the original design reference never asked for at all.
The upload page (`_shell.admin.projects.upload.tsx`) hands its parsed `LogbackParseResult` to the
`new` route through an in-memory handoff (`wizardPrefill.ts` — a module-level variable, since it's
always the same same-tab SPA navigation, nothing needs to survive a reload) instead of a session
draft.

### 10.5 Converting away from TanStack Start / SSR

The design this frontend's look was ported from (a Lovable-generated prototype) ran on TanStack
**Start** with a Nitro SSR server, defaulting to a Cloudflare build target — a second Node runtime
this app would have had to run alongside Spring Boot in production, breaking "standalone Spring
Boot app." It was converted to a plain client-rendered SPA instead: dropped
`@tanstack/react-start`, `nitro`, and the Lovable-platform-specific dev/error-reporting glue;
kept `@tanstack/react-router` (file-based routing works identically without Start) and everything
else. `frontend/index.html` + `src/main.tsx` (standard Vite React entry) replaced `__root.tsx`'s
`shellComponent`/`HeadContent`/`Scripts` SSR document — the pre-paint theme/skin `localStorage` IIFE
moved into `index.html`'s `<head>` verbatim, same flash-of-wrong-theme fix as before. Per-route
`head()` SEO/OG-tag metadata (SSR-oriented, irrelevant for an internal admin tool) was dropped, not
ported.

## 11. Known gotchas — read before touching these areas

**`DateTimeFormatter.parseUnresolved` needs a `YEAR_OF_ERA` fallback.** See §9.3.

**`flyway-database-h2` is not a real Maven artifact.** H2 support ships inside `flyway-core`
itself. See §2.1.

**Boot 4's modularized auto-config bites silently.** See §2.1 — no exception, no warning, the
integration just doesn't activate.

**`SpaController`'s route list must stay in sync with the frontend's real routes.** It forwards
exactly `/`, `/login`, and `/admin/**` to `index.html` (§4, §6) — an explicit list, not a
catch-all regex, because the backend needs to know which paths are SPA client routes (forward to
the shell) versus real static assets (404 if actually missing) versus `/api/**`/actuator/swagger
(their own handlers). Adding a new *top-level* frontend route outside `/admin/**` means adding it
to this list too, or a direct browser load/refresh of that route will 404 instead of resolving via
the client-side router.

**The `frontend-maven-plugin`'s Node download has been observed to fail intermittently on at least
one Windows dev machine** (antivirus real-time scanning intercepting the freshly-extracted
`node.exe`, or a corrupted npm cache download manifesting as `ENOTEMPTY`/"tarball data ... seems to
be corrupted" warnings) — always transient so far; deleting `frontend/node` (and, if `npm`-level
errors show up, `frontend/node_modules` + `frontend/package-lock.json`) and re-running the build
has resolved it every time. See §3.

**The frontend's Basic-auth-in-`sessionStorage` login (§10.2) is a deliberate, marked
simplification, not an oversight** — see the `// ponytail:` comment on `authStorage.ts`. Don't
"fix" it into a bigger auth system without a reason; do reach for it if this UI is ever meant to be
reachable by more than one trusted admin.

## 12. Database & migrations

Flyway locations: `classpath:db/migration/common,classpath:db/migration/{vendor}`
(`application.yml`). `common/` is the portable baseline (every SQL database); `{vendor}` resolves to
whichever engine is actually running — `postgresql/` (prod) and `h2/` (dev) both exist as vendor
overrides.

| Migration | Contents |
|---|---|
| `common/V1__init.sql` | `project`, `log_source`, `filter_field`, `line_pattern` tables |
| `common/V2__indexes.sql` | Supporting indexes |
| `common/V4__log_file.sql` | Adds `log_file` table (the node → per-output split, §5); migrates each existing `log_source`'s single live/backup/pattern config into a `log_file` row labeled `"Application"`, reusing the `log_source` row's own id as the new `log_file` id (safe — different table's PK space); then drops those columns from `log_source`. |
| `h2/V5__dev_seed_project.sql` | Dev-only convenience data: since it lives under the `h2` vendor location it only ever runs against the in-memory H2 dev database, never prod. Seeds one ready-to-search project ("360 API": one node/log-file, a `tid`/"Trace ID" `EXACT_TOKEN` filter field, and a `LinePattern` derived the same way the wizard's "analyze sample line" step would) so a fresh `mvnw spring-boot:run` has something to search immediately. |

(There is no `V3` — it used to hold Spring Session JDBC's tables, removed along with the Thymeleaf
UI that needed sessions; the version number is left as a gap rather than renumbered, since Flyway
doesn't require contiguous versions and renumbering an already-applied migration on any real
deployment would break its checksum validation.)

**To add support for another database** (MySQL, Oracle, SQL Server): add its JDBC driver +
`flyway-database-<db>` module to `pom.xml`, point `spring.datasource.*` at it, and add
`db/migration/<db>/Vn__*.sql` for anything vendor-specific. **Known gap:** SQL Server's `TIMESTAMP`
type means "rowversion," not a date/time column — a SQL Server target needs
`db/migration/sqlserver/` using `DATETIME2` instead.

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
- No view-mode toggle (Cards vs. a dense Stream layout) or group-by on the search page — both real
  features of the old Thymeleaf UI, intentionally not ported in the initial React rewrite (§10.3).
  Add them back as new frontend features if a future task asks for them.
- No CI/CD configuration, no Docker/containerization files.
