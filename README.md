# Worktime Tracker — Spring Boot / Hibernate JPA / PWA

A small **installable, responsive employee time-tracking app** developed for an AI-assisted programming exercise. It tracks individual timers with task descriptions, keeps records on disk across application restarts, and calculates per-employee reports for a selected period. **Only fictional data** is used.

![Java 21](https://img.shields.io/badge/Java-21-ec6c31) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.16-6db33f) ![JPA](https://img.shields.io/badge/ORM-Hibernate%2FJPA-blue) ![PWA](https://img.shields.io/badge/mobile-PWA-6555e9)

## Features

- **Users** lists users, creates them in a dialog and links to a separate edit page with session counts and durations grouped by project and local start month.
- **Projects** lists projects, task counts, estimated time, executed time, and Working or Idle status. The project detail shows the same totals and lets you create tasks with an estimate in minutes. Executed time sums saved work sessions and includes elapsed time for running sessions. Project timers belong to the user selected in the toolbar.
- The toolbar shows the active project, task, elapsed time and server connection status. Starting a task creates a new session in H2 before it appears in the toolbar. The browser keeps the session ID and start time in local storage.
- **Reports** lists sessions with user, project, status and local start-date filters. It shows the total duration and a breakdown by user across all filtered pages. Export Excel downloads the checked sessions that match the current filters, in sort order. Older sessions without a task appear as **Unassigned**.
- Install as a **PWA** on compatible Android/desktop browsers, or add it to the iPhone Home Screen through Safari. Responsive UI with a standalone icon and an offline explanation page.
- No authentication, external APIs, or real employee records: this is intentionally a small demo.

## Technology

**Implementation baseline:** Java 21, Spring Boot, Hibernate/JPA and an embedded persistent H2 database.

| Layer | Choice | Rationale |
| --- | --- | --- |
| Server | **Java 21**, **Spring Boot 3.5.16** | Familiar, maintainable REST APIs and strong automated testing support. |
| ORM | **Hibernate / Spring Data JPA** | Entities, repositories and transactional business operations. |
| Database | **H2 embedded file database** | Durable, single-process data without installing PostgreSQL. |
| UI | Vanilla JavaScript, HTML and CSS | Fast startup; no Node build or frontend framework required. |
| PWA | Web App Manifest + Service Worker + PNG icons | Installability and cached offline information page. |
| Tests | JUnit 5, Spring Boot Test, MockMvc, AssertJ | HTTP contract and end-to-end persistence/restart scenarios. |

> The small app uses H2 so reviewers can start it with a single Maven command. For a multi-instance or production deployment, use PostgreSQL and managed schema migrations. This demo uses `hibernate.ddl-auto=update` to create the tables automatically; this is **not** a production migration strategy.

## Run locally

**Prerequisites:** JDK **21** and Maven **3.9+**. No separate database server, frontend build tools or cloud account required.

```bash
# From the directory containing pom.xml:
mvn spring-boot:run
```

Open **http://localhost:8080/**. H2 creates persistent files in `./data/worktime.mv.db` (directory included in the project). Stop and restart the app with the same working directory and all data and running timers remain available.

On first startup against a database, migration `001-demo-projects` adds two fictional users, two projects, and six tasks with estimates. It also creates the initial demo user on an empty database. The migration records its version in H2 and runs once per database. Set `WORKTIME_DEMO_DATA_ENABLED=false` before starting to skip demo data. With demo data disabled on a fresh database, create user #1 from **Users** to start a task.

```bash
mvn clean package
java -jar target/worktime-tracker-1.0.0.jar
```

You can change the HTTP port and database path:

```bash
# Linux/macOS
PORT=8090 WORKTIME_DB_URL='jdbc:h2:file:/absolute/path/worktime;DB_CLOSE_ON_EXIT=FALSE' mvn spring-boot:run

# PowerShell (set vars before running Maven)
$env:PORT='8090'
$env:WORKTIME_DB_URL='jdbc:h2:file:C:/worktime/data/worktime;DB_CLOSE_ON_EXIT=FALSE'
mvn spring-boot:run
```

Keep the *same* H2 file path on subsequent launches. Never commit `.mv.db` files. To **reset** the demo, shut down the app first, then remove `data/worktime.mv.db` and (if present) `data/worktime.trace.db`.

### Docker (optional)

```bash
docker compose up --build
```

Open `http://localhost:8080`. The Docker named volume `worktime_data` stores the H2 database outside the container so data survives a recreated container. `docker compose down` preserves the volume, while `docker compose down -v` **deletes** it.

## Install on mobile (PWA)

1. Start the server and open the application on your device. On the same PC, `http://localhost:8080` works for PWA testing.
2. For a **real phone**, deploy the app behind **HTTPS** (a certificate is required for service workers on non-localhost origins). You can also use an HTTPS development tunnel. Visiting `http://<computer-LAN-IP>:8080` can show the website, but usually **cannot install the PWA**.
3. On Android/Chrome, choose **Install app** (browser menu or the app's button if offered). On iPhone/Safari, choose **Share → Add to Home Screen**.
4. Open the installed app to use employee management, timers, and reports.

**Offline behavior:** The cached app shell can show the locally stored timer. If Stop cannot reach the server, the browser retains the session ID and the time Stop was pressed. The app retries automatically after a successful connection check while it is open, including when it is reopened. The browser clears the saved session after the backend confirms the stop. Starts, reports and data changes require the server. The service worker caches static files only.

## Automated tests

```bash
mvn clean test
# or full verification:
mvn clean verify
```

Tests are configured for GitHub Actions: `.github/workflows/ci.yml`. An optional dependency-free PWA test can run with `node --test scripts/test-pwa.cjs` (Node 22). They use an **isolated in-memory H2 database**, a **controllable clock**, and a **temporary on-disk H2 file** for the application-restart test. Production data is untouched.

Key scenarios in `src/test/java/md/dipro/worktime/`:

1. Static UI, manifest, service worker and health endpoint.
2. Create/list employees, including name normalization.
3. Start/stop timer and exact work duration.
4. Stop then restart the timer for another task; both sessions remain in reports.
5. **Real Spring application-context shutdown/restart** against the same H2 file; recover a running timer, stop it and verify its history after one more restart.
6. **Empty report with zero employees**, and an employee with no sessions (zero total).
7. Multiple employees and company total.
8. Clip durations at **both** report date boundaries.
9. Count running timers only up to a single current-time snapshot (not into the future).
10. Reject duplicate starts and stops without a running timer (`409`), including simultaneous start attempts.
11. Validate names/descriptions/date range, unknown employee (`404`) and timestamps without offsets (`422`).
12. Interpret timezone offsets correctly.

**Local verification (8 November 2026):** `mvn clean verify` passed 16 Java tests on Windows with JDK 21.0.7 and Maven 3.9.16. The tests cover the demo migration across restarts, active and completed project time, stop retry, and Excel export. The JavaScript syntax check and all four PWA tests passed.

## HTTP API

| Method | Endpoint | Expected behavior |
| --- | --- | --- |
| `GET` | `/api/health` | `{"status":"ok"}` |
| `GET` | `/api/employees` | List employees. |
| `POST` | `/api/employees` | Add `{"name":"Alex Morgan"}` (`201`). |
| `PUT` | `/api/employees/{id}` | Rename a user. |
| `GET, POST` | `/api/projects` | List project summaries or create a project. |
| `GET` | `/api/projects/{id}` | Read a project and its tasks. |
| `POST` | `/api/projects/{id}/tasks` | Add a task with a description. |
| `POST` | `/api/tasks/{id}/sessions` | Create a work session for `userId`, which defaults to 1. |
| `GET` | `/api/sessions/active` | Read the active session for `userId`, which defaults to 1. |
| `POST` | `/api/sessions/{id}/stop` | Stop a session belonging to `userId`, which defaults to 1. Repeating the request returns the saved result. |
| `GET` | `/api/sessions` | List work sessions, optionally filtered by `userId`. |
| `POST` | `/api/sessions/export` | Export an ordered JSON array of session IDs as an Excel workbook. |
| `GET` | `/api/employees/{id}/timer` | Active timer or `{ "running": false, "entry": null }`. |
| `POST` | `/api/employees/{id}/timer/start` | Start `{"description":"Fix UI"}` (`201` or `409`). |
| `POST` | `/api/employees/{id}/timer/stop` | Stop timer (`200` or `409`). |
| `GET` | `/api/reports?start=...&end=...` | Records and totals for each employee and whole company. |

`start` and `end` must be **ISO 8601 timestamps with an explicit timezone offset**, e.g. `2026-11-08T00:00:00Z` or `2026-11-08T12:00:00+02:00`. The interval is **half-open** `[start, end)`. The UI builds it from local calendar dates and converts to ISO instants, including DST boundaries. Durations are reported as `seconds_in_period` and `total_seconds` (fractional seconds if applicable).

Example:

```bash
curl -X POST http://localhost:8080/api/employees \
  -H 'Content-Type: application/json' -d '{"name":"Alex Morgan"}'
curl -X POST http://localhost:8080/api/employees/1/timer/start \
  -H 'Content-Type: application/json' -d '{"description":"Implement employee report"}'
curl -X POST http://localhost:8080/api/employees/1/timer/stop
curl 'http://localhost:8080/api/reports?start=2026-11-08T00%3A00%3A00Z&end=2026-11-09T00%3A00%3A00Z'
```

`/api/reports` returns every employee, even without entries. A session partially overlapping the report period contributes only its intersection with the selected range. A running session uses the request's single `generated_at` snapshot as its effective stop time.

## Code organization

```text
src/main/java/md/dipro/worktime/
  WorktimeTrackerApplication.java            # Spring Boot entry, injectable Clock
  api/WorktimeController.java                # HTTP endpoints
  api/ApiDtos.java                           # validated input and JSON output contracts
  api/ApiExceptionHandler.java               # consistent 404/409/422 errors
  domain/Employee.java, TimeEntry.java       # JPA entities
  repository/*Repository.java                # Spring Data repositories, row lock, overlap query
  service/WorktimeService.java               # transaction and report rules
src/main/resources/
  application.properties                     # durable H2 config
  static/index.html                          # responsive app shell
  static/static/{app.js,styles.css}          # browser UI
  static/{manifest.webmanifest,service-worker.js,offline.html}
  static/icons/{icon-192.png,icon-512.png}
scripts/test-pwa.cjs                        # Node PWA smoke tests
src/test/java/md/dipro/worktime/
  WorktimeApiTest.java                       # MockMvc scenarios with deterministic time
  PersistenceRestartTest.java               # real close/open with on-disk DB
.github/workflows/ci.yml                     # Maven tests on GitHub
Dockerfile, compose.yaml                     # optional container deployment
```

### Important design decisions

- **Persist timestamps, not ticks:** saving `started_at` immediately is enough to recover elapsed time after restarting the browser or server. `stopped_at` is nullable until stopped.
- **Concurrency:** `startTimer()` and `stopTimer()` execute within database transactions. Both obtain a **pessimistic write lock on the same employee row**, so simultaneous requests for that employee serialize. Two independent employees can track at the same time. This version targets one running server backed by an H2 file; use a shared transactional DB if scaling horizontally.
- **Report accuracy:** `overlap = max(0, min(periodEnd, stoppedAtOrNow) - max(periodStart, startedAt))`. Time is calculated server-side from a consistent snapshot. Date/time offsets are parsed explicitly.
- **Consistent API contract:** the REST API uses predictable routes and `snake_case` JSON keys, configured through Jackson, to keep the Java backend and browser client aligned.
- **Defensive frontend:** task/employee text is inserted into the DOM using `textContent` rather than `innerHTML`, limiting script-injection risks.
- **Persistence scope:** this Java application stores its records in an embedded H2 database on disk. Keeping the same database file path preserves data and active timers across application restarts.

## AI-assisted development

**Tools used:** OpenAI **ChatGPT (GPT-6)** assisted with Java, JPA, reporting, PWA behavior and test cases. **OpenAI Codex** verified the build, fixed the Maven test JVM configuration and implemented the Users, Projects and Reports workflow.

Example prompts documenting the Java/Spring Boot development workflow:

> "Build an employee work-time tracking application using Java 21, Spring Boot, Hibernate/Spring Data JPA and a persistent embedded H2 database. Design a small REST API with consistent `snake_case` JSON responses and a responsive browser interface."
>
> "A running timer must survive restarting the Java application. Store start and optional stop timestamps; reject a duplicate start with HTTP 409. Protect against two concurrent start requests for the same employee."
>
> "Implement date-range totals on the server. Clip every time interval to the selected reporting window, and include employees with zero time and an entirely empty report. Use a controllable Clock for tests."
>
> "Make the responsive frontend an installable PWA. Add a manifest, 192px/512px icons, a service worker and an offline information page. Never cache or queue timer API requests."
>
> "Create Spring Boot integration tests for timer start/stop/restart, persistence after closing and reopening a Spring application context, empty reports, timezone offsets and concurrent start attempts."
>
> "Add Users, Projects, and Reports pages. Start and stop timers from the task list. Create the work session for user ID 1 before showing the timer, and keep the session in local storage until the backend confirms the stop. Add filters, sorting, and Excel export for work sessions."

### Problems spotted and addressed during review

| Issue | Why it matters | Resolution |
| --- | --- | --- |
| Potential double start under simultaneous requests | Two HTTP threads can both observe 'not running' before either writes. | Use `@Transactional` plus `@Lock(PESSIMISTIC_WRITE)` on the employee row before checking/inserting. Add a concurrency test. |
| A Java-side timer counter would reset after server restart | In-memory timer state is lost. | Persist `startedAt`/`stoppedAt` in H2, then read the active row on each page load. Add a real application restart test. |
| Summing whole sessions for one calendar day overstates hours | A session may cross midnight or reporting boundaries. | Query overlapping sessions and sum only each interval's intersection with `[start,end)`. |
| A naive PWA cache might replay stale `/api/reports` and timer state | Users could see wrong totals or assume offline time was saved. | Restrict service-worker caching to the static shell, leave `/api/**` network-only; show an offline page. |
| Missing/invalid date offsets and empty reports | Ambiguous timezone or null/incorrect totals. | Require offset-aware dates, validate end > start, always return zero-valued employees. Add tests. |

The table describes design and code review fixes. During local verification, the API test suite stalled after Mockito attempted to attach its agent dynamically. Surefire now loads the Maven-resolved Mockito agent at JVM startup. A fresh `mvn clean verify` completed with the existing tests.

## Known limits / next steps

- Intended for a small, single-server technical exercise. No authentication, time edits, leave approvals, or employee removal.
- H2 + `ddl-auto=update` is convenient for review but should be changed to PostgreSQL + Flyway for production deployments.
- Starting a timer requires a live connection. An offline stop remains pending until the app reconnects and the server confirms it.
- GitHub Actions and PWA installation on a physical device still need verification.

## License

Demo source code supplied for evaluation. No external service or proprietary assets required.
