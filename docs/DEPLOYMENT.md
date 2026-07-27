# Deployment guide

Two supported ways to run this app in production: a **Docker container** (recommended — see
[Dockerfile](../Dockerfile)/[docker-compose.yml](../docker-compose.yml)) or a **traditional WAR**
on an external servlet container. Both produce the exact same application; pick whichever fits
your infrastructure. Read [CLAUDE.md](../CLAUDE.md) first if you haven't — this guide assumes you
know the app's shape (search API, admin wizard, single admin login).

## Before either path: what production needs

Regardless of how you deploy, the packaged jar/WAR is identical and takes all
production-specific configuration from environment variables — nothing to edit in the artifact
itself.

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | Must be `prod` |
| `JDBC_DATABASE_URL` | yes | JDBC URL — its scheme picks the engine (`jdbc:postgresql:`, `jdbc:mysql:`, `jdbc:sqlserver:`) |
| `JDBC_DATABASE_USERNAME` | yes | Database user |
| `JDBC_DATABASE_PASSWORD` | yes | Database password |
| `FLYWAY_TIMESTAMP_TYPE` | only for SQL Server | `DATETIME2` for SQL Server; leave unset (defaults to `TIMESTAMP`) for Postgres/MySQL — see [application-prod.yml](../src/main/resources/application-prod.yml) |
| `LOGUTY_ADMIN_USERNAME` | yes | The one admin account's username |
| `LOGUTY_ADMIN_PASSWORD` | yes | The one admin account's password |

The app **fails fast at startup** if the admin credentials are missing, and Flyway migrates the
schema automatically on every boot — no manual migration step, no manual database bootstrapping
beyond having an empty database that already exists (Flyway can create tables, not the database
itself).

You also need the database to already exist and be reachable — this app doesn't provision one.

---

## Option A — Docker / Docker Compose

Build once, push to a registry, then pull and run on each target environment — the build machine
and the deploy target don't need to be the same machine, and a deploy target never needs the
source tree, only `docker-compose.yml` + `.env`.

### Prerequisites

- Docker + Docker Compose plugin, on both the build machine and every deploy target.
- A container registry both sides can reach (Docker Hub, ECR, GHCR, a private registry, ...) and
  credentials to push to it (build machine) / pull from it (deploy targets).
- A reachable Postgres/MySQL/SQL Server instance per environment (already set up — this compose
  file does not bundle one).
- The host path(s) where the target apps' logs/backups live on each deploy target, so they can be
  bind-mounted in.

### 1. Build machine: build, tag, and push

`docker-compose.yml` is deploy-only (`image:`, no `build:`) — building uses the
[Dockerfile](../Dockerfile) directly with plain Docker, same as the reference compose file you
based this on assumes elsewhere in its own release process:

```bash
docker login your-registry                 # once per machine/session
docker build -t your-registry/logutility:1.5.0 .
docker push your-registry/logutility:1.5.0
```

Bump the tag per release the way `in10sdev/360-api:dev-360-1.5.0-latest` does in the reference file
you shared — avoid reusing one fixed tag (e.g. `latest`) for anything you actually want to roll
back to later. `JDBC_DATABASE_URL` etc. are never baked into the image; they're only read at
container start (step 2), so the same image is reusable as-is across every environment.

### 2. Each deploy target: configure and pull

Copy just `docker-compose.yml` and `.env.example` to the target (no source tree, no Dockerfile
needed — this machine never builds anything):

```bash
cp .env.example .env
```

Edit `.env` for *this* environment specifically:
- `IMAGE_NAME` / `IMAGE_TAG` — must match exactly what the build machine pushed in step 1.
- `JDBC_DATABASE_URL` / `_USERNAME` / `_PASSWORD` — this environment's database (uncomment the
  `jdbc:mysql:...` or `jdbc:sqlserver:...` line instead of Postgres if that's the engine here, and
  set `FLYWAY_TIMESTAMP_TYPE=DATETIME2` for SQL Server).
- `LOGUTY_ADMIN_USERNAME` / `LOGUTY_ADMIN_PASSWORD` — real credentials, not the dev defaults.
- `LOGS_HOST_ROOT` — the parent directory on *this* host that covers every target project's
  logs/backups (see the comment in `docker-compose.yml` — it's bind-mounted read-only at the
  identical path inside the container, so wizard paths need no translation and adding a new
  project under this root needs no redeploy).
- `LOG_HOST_DIR` — where *this app's own* logs land on this host (not the target apps' logs
  above). Must exist and be writable by uid 10001 before the first `docker compose up`:
  `mkdir -p "$LOG_HOST_DIR" && chown 10001:10001 "$LOG_HOST_DIR"`.

```bash
docker login your-registry                 # once per machine/session, if the registry is private
docker compose pull                        # fetches ${IMAGE_NAME}:${IMAGE_TAG} - no build
docker compose up -d
```

### 3. Verify

```bash
docker compose logs -f log-utility-app     # watch startup; confirm Flyway migrated successfully
curl http://localhost:8585/logutility/actuator/health # host port from the "8585:8080" mapping in docker-compose.yml
```

Then open `http://<host>:8585/logutility` in a browser, sign in at `/logutility/login` with the
admin credentials from `.env`, and add your first project via the admin wizard. The app is served
under the fixed `/logutility` context path in every deployment form — see `server.servlet.context-path`
in `application.yml`.

This app's own logs (as opposed to the target apps' logs it searches) go to console — captured by
`docker compose logs` — plus a rolling app log and a rolling error-only log inside the container at
`/opt/logutility/logs/` (see [logback-spring.xml](../src/main/resources/logback-spring.xml)),
bind-mounted to `LOG_HOST_DIR` on the host so they survive `docker compose pull && up -d`
recreating the container. Read them either from the host directly or via the container:

```bash
tail -f "$LOG_HOST_DIR"/logutility.log                                 # or logutility-error.log
docker compose exec log-utility-app tail -f logs/logutility.log        # equivalent, from inside
```

### 4. Shipping an update

On the build machine: bump `IMAGE_TAG`, repeat step 1. On each deploy target: update `IMAGE_TAG` in
`.env` to match, then:

```bash
docker compose pull
docker compose up -d
```

Flyway re-applies only new migrations on restart; existing data is untouched. Keeping the previous
tag around (rather than overwriting `latest`) is what makes `IMAGE_TAG=<previous>` + `docker compose
pull && up -d` a rollback.

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| `docker compose pull` fails with "not found" or "unauthorized" | `IMAGE_NAME`/`IMAGE_TAG` in this target's `.env` doesn't match what was pushed, or `docker login` wasn't run on this machine for a private registry |
| Container healthcheck failing / can't reach `/logutility/actuator/health` | Check `docker compose logs log-utility-app` — usually a datasource connection failure (wrong `JDBC_DATABASE_URL`/credentials, or DB not reachable from the container's network) |
| App starts but every "Test path" check fails in the wizard | `LOGS_HOST_ROOT` doesn't actually cover the path you entered, or the container's non-root user (uid 10001) lacks read permission on the host directory — see `ls -la` on the host path |
| Container restarts in a loop right after `docker compose up` | Almost always a missing/blank required env var (`LOGUTY_ADMIN_*` or `JDBC_DATABASE_*`) — the app fails fast on those by design; check `docker compose logs log-utility-app` for the exact message |
| SQL Server: columns come back as binary garbage / inserts fail | `FLYWAY_TIMESTAMP_TYPE` wasn't set to `DATETIME2` before the first startup — see the note in `application-prod.yml` |
| App runs fine but no files show up under `LOG_HOST_DIR` (a logback permission error appears near the top of `docker compose logs`) | `LOG_HOST_DIR` on the host isn't writable by uid 10001 — `chown 10001:10001 "$LOG_HOST_DIR"` and restart |

No built-in HTTPS — put a reverse proxy (nginx, an ALB/ingress, etc.) in front for TLS in any real
deployment; this container only serves plain HTTP on 8080.

### If you're building in CI instead of on a laptop

Same two commands as step 1 — wire `docker build` + `docker push` into your CI pipeline after
tests pass, with the tag set from the CI run (commit SHA, version tag, etc.) rather than hardcoded
anywhere.

---

## Option B — Traditional WAR on an external servlet container

### Prerequisites

- **JDK 21** available wherever you *build* the WAR (not necessarily the deploy target).
- **A Jakarta EE servlet container — Tomcat 10.1+ specifically.** Spring Boot 4 uses the
  `jakarta.servlet.*` namespace; Tomcat 9 and earlier (`javax.servlet.*`) will fail to deploy this
  WAR at all (`ClassNotFoundException`/`NoClassDefFoundError` on servlet classes). If you're stuck
  on Tomcat 9, use Option A instead.
- A JDK 21 toolchain entry — the `war21` Maven profile uses `maven-toolchains-plugin` to guarantee
  Java 21 bytecode regardless of which JDK is running Maven itself.

### Steps

1. **Register the JDK 21 toolchain** (one-time, on the build machine) — create or edit
   `~/.m2/toolchains.xml`:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <toolchains>
     <toolchain>
       <type>jdk</type>
       <provides>
         <version>21</version>
       </provides>
       <configuration>
         <jdkHome>/path/to/jdk-21</jdkHome>
       </configuration>
     </toolchain>
   </toolchains>
   ```

   On Windows, `jdkHome` is something like `C:\Program Files\Eclipse Adoptium\jdk-21.0.x`. Without
   this, `-Pwar21` fails immediately with a "Cannot find matching toolchain" error.

2. **Build**

   ```bash
   ./mvnw clean package -Pwar21        # Linux/macOS
   mvnw.cmd clean package -Pwar21      # Windows
   ```

   Produces `target/logutility.war`. This also runs the frontend build (Maven downloads its
   own pinned Node automatically) and the full test suite unless you add `-DskipTests`. Embedded
   Tomcat is marked `provided` in this profile — the target container supplies it — but the
   Postgres/MySQL/SQL Server JDBC drivers are bundled inside the WAR's `WEB-INF/lib`, so you don't
   need to add driver jars to the container yourself.

3. **Deploy as-is — no renaming.** The frontend's asset/API paths are all built relative to
   `/logutility` (see `frontend/vite.config.ts`'s `base`), matching this app's fixed
   `server.servlet.context-path`. A traditional WAR deployment doesn't honor that Spring property
   though — the container derives the context path from the deployed file name instead — which is
   exactly why the war21 profile's `finalName` is `logutility`: dropping the file in as-is already
   deploys at `/logutility`.

   ```bash
   cp target/logutility.war /path/to/tomcat/webapps/logutility.war
   ```

   (Remove any existing `webapps/logutility` directory first so Tomcat doesn't serve a stale one
   alongside it. If your container instead assigns context paths some other way — e.g. a
   `context.xml` `path` attribute — point that at `/logutility` instead of relying on the file
   name.)

4. **Configure environment variables for the container process.** Tomcat inherits the OS
   environment of whatever started it, so set the variables from the table above however your
   platform normally does that — for a plain Tomcat install without a service manager, add a
   `bin/setenv.sh` (Linux/macOS) or `bin/setenv.bat` (Windows), which Tomcat's startup script
   sources automatically if present:

   ```bash
   # tomcat/bin/setenv.sh
   export SPRING_PROFILES_ACTIVE=prod
   export JDBC_DATABASE_URL=jdbc:postgresql://your-db-host:5432/logutility
   export JDBC_DATABASE_USERNAME=logutility
   export JDBC_DATABASE_PASSWORD=change-me
   export LOGUTY_ADMIN_USERNAME=admin
   export LOGUTY_ADMIN_PASSWORD=change-me
   ```

   ```bat
   :: tomcat\bin\setenv.bat
   set SPRING_PROFILES_ACTIVE=prod
   set JDBC_DATABASE_URL=jdbc:postgresql://your-db-host:5432/logutility
   set JDBC_DATABASE_USERNAME=logutility
   set JDBC_DATABASE_PASSWORD=change-me
   set LOGUTY_ADMIN_USERNAME=admin
   set LOGUTY_ADMIN_PASSWORD=change-me
   ```

   If Tomcat runs as a Windows Service instead, set these under the service's environment
   variables in `tomcat9w.exe`/the service manager, not `setenv.bat` (services don't source it).

5. **Start Tomcat and verify** — same checks as the Docker path:

   ```bash
   curl http://localhost:8080/logutility/actuator/health
   ```

   Then sign in at `/logutility/login` and confirm the admin wizard can reach it.

6. **Log/backup paths**: unlike the container path, there's no bind-mount step here — the servlet
   container process already runs directly on the host filesystem, so whatever `liveLogPath` /
   `backupRootPath` you enter in the wizard just needs to be readable by the OS user Tomcat runs
   as.

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| `mvn -Pwar21` fails with "Cannot find matching toolchain" | `~/.m2/toolchains.xml` missing/wrong `jdkHome` — step 1 |
| WAR deploys but every page 404s except `/logutility` | Deployed under the wrong context path — check the deployed file/app name matches `logutility` — step 3 |
| `ClassNotFoundException: javax.servlet...` or the app never starts | Container is Tomcat 9 or older — needs Tomcat 10.1+ (Jakarta EE) |
| App starts but immediately shuts down | Missing/blank required env var — check `tomcat/logs/catalina.out` for the exact fail-fast message |

---

## After either deployment

Sign in with the admin credentials, then use the admin wizard (`/admin/projects/new`) to add your
first project: upload its `logback-spring.xml` or fill in nodes/log files manually, define filter
fields, and let the wizard derive the line pattern from a sample log line. See
[docs/PROJECT_REFERENCE.md](PROJECT_REFERENCE.md) §9–10 for exactly what each wizard step does.
