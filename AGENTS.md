# Codex project context — Worktime Tracker

## Mission

Finish and verify an existing small employee work-time tracking web application for an AI-assisted programming take-home task. The application must be a working, easy-to-run deliverable with readable code, automated tests, and an accurate English README. **Continue and improve the current implementation; do not rebuild it from scratch or switch stacks.** Use only fictional data.

## Original assignment / acceptance criteria

The app must let users:
1. Add employees.
2. Start a work timer for an employee with a required task description.
3. Stop a timer and preserve the completed entry.
4. View time records and per-employee totals for a selected period.
5. Recover data after the application process is restarted.

No authentication, roles, or complex features are required. An ordinary web UI is enough. The employer also requires a README with run instructions, AI tools/models **actually used**, representative AI prompts, and **real** code issues found and fixed; automated tests must cover main scenarios, specifically timer restart and an empty report. Final submission must include a **public repository URL**; live demo is optional.

## Existing implementation (inspect before modifying)

The repository already contains a Spring Boot application and responsive PWA:
- Java 21, Spring Boot 3.5.16, Maven.
- Spring Web REST API, Hibernate / Spring Data JPA, Jakarta Validation.
- H2 **file-based** database at `./data/worktime.mv.db` by default (configured in `src/main/resources/application.properties`); intended to survive server restart.
- Vanilla HTML/CSS/JS in `src/main/resources/static/`.
- PWA: `manifest.webmanifest`, `service-worker.js`, `offline.html`, and `icons/`.
- Spring/JUnit/MockMvc tests in `src/test/java/md/dipro/worktime/`.
- Node PWA tests: `scripts/test-pwa.cjs`.
- Dockerfile, Docker Compose and GitHub Actions workflow.
- An existing English `README.md` that must remain truthful and be updated after verification.

Key files:
- `src/main/java/md/dipro/worktime/api/WorktimeController.java`
- `src/main/java/md/dipro/worktime/api/ApiDtos.java`
- `src/main/java/md/dipro/worktime/api/ApiExceptionHandler.java`
- `src/main/java/md/dipro/worktime/service/WorktimeService.java`
- `src/main/java/md/dipro/worktime/domain/{Employee,TimeEntry}.java`
- `src/main/java/md/dipro/worktime/repository/`
- `src/main/resources/static/static/{app.js,styles.css}`
- `src/test/java/md/dipro/worktime/{WorktimeApiTest,PersistenceRestartTest}.java`

Existing REST routes (preserve unless a real bug requires a documented change):
- `GET /api/health`
- `GET, POST /api/employees`
- `GET /api/employees/{id}/timer`
- `POST /api/employees/{id}/timer/start` with `{ "description": "..." }`
- `POST /api/employees/{id}/timer/stop`
- `GET /api/reports?start=<offset-aware ISO datetime>&end=<offset-aware ISO datetime>`

JSON properties use `snake_case` (Spring Jackson configuration). Periods use a half-open `[start, end)` interval. Reports include employees with zero time. A running timer's elapsed time is based on stored timestamps, not an in-memory counter. Timers are limited to one active entry per employee; transactions and a pessimistic employee-row lock are used when starting/stopping.

## Work plan — execute, do not just propose

1. **Inspect all existing code and tests** and establish the actual current build/test status. Avoid unnecessary renaming, refactoring, framework upgrades or feature creep.
2. Run `mvn clean verify` with JDK 21. Fix **actual compilation problems, test failures, and runtime bugs**. Do not weaken/delete failing tests just to get green CI. If dependencies/network prevent running Maven, say so precisely and provide a reproducible follow-up; never claim success without evidence.
3. If Node is present, run `node --check src/main/resources/static/static/app.js` and `node --test scripts/test-pwa.cjs`.
4. Launch the app (`mvn spring-boot:run`), verify the main UI and test API calls. Test create employee, start, stop, restart timer as a **new entry**, retrieve report, report without records, and application process restart with the same database. Verify the running timer also survives a server restart; show the current duration based on saved `started_at`.
5. Check edge cases: empty/invalid names or task descriptions; `404` unknown employee; `409` duplicate start/stop; concurrent starts; report interval validation; sessions crossing period boundaries; timezone/DST handling; active sessions at report snapshot; employee with zero time; no employees; user-facing errors.
6. PWA: verify manifest, correct 192/512 icons, service-worker registration, responsive/mobile layout, installability on supported browsers. Cache only appropriate static assets; **never cache or queue `/api/**` calls or claim that timer records are saved offline**. Show an informative offline fallback. PWA installation on a real phone generally requires HTTPS.
7. Update README with accurate run/test commands, architecture, technical decisions, limitations, actual verification evidence and exact issues you truly found and fixed. Explicitly distinguish review-based fixes from reproduced failing tests. Document the AI-assisted workflow with actual model/tool identities; **do not invent tools, prompts, bugs, tests, screenshots, demos, or CI success**. Existing development used OpenAI ChatGPT GPT-6; Codex can be listed as used only when this agent actually performs work.
8. Prepare a clean Git repository suitable for public submission: no database files, secrets, logs, local IDE files, or private data. If GitHub access is available and publication is authorized, create/push a public repository and report its actual URL; otherwise supply exact commands to push it. A demo URL is optional.
9. Conclude with a concise factual summary: files modified, actual bugs fixed, commands/tests executed and results, any remaining limitations, how to run the application, and what remains before submission.

## Non-goals and quality rules

- Do not migrate to FastAPI, Node backend, Flutter, React Native, or a different architecture.
- Keep Spring Boot + Hibernate/JPA and the PWA; H2 is acceptable for this small exercise. PostgreSQL is not required.
- Preserve readable layers: controller/DTO -> service -> repository/entities.
- Keep data creation fictional; do not add real employee records.
- Avoid unnecessary authentication or roles.
- Never write secrets into code, README or Git commits.
- Prefer deterministic tests (controllable Clock, isolated database). Test persistence against a temporary on-disk database rather than only in-memory H2.
- Make minimal, focused fixes; explain their rationale and demonstrate verification.

## Expected deliverable

A functioning Spring Boot / Hibernate-JPA / PWA application, passing automated tests, truthful README documenting AI assistance and actual fixes, and a repository ready for (or already at) a public GitHub URL.
