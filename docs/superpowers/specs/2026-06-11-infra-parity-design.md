# Infra Parity: Flyway, Dockerfile, CI/CD — Design

**Date:** 2026-06-11
**Goal:** Bring DocPlatform's infrastructure to (and past) ShopHub's level: versioned DB migrations, a production Docker image, and a CI pipeline that tests against real MySQL and publishes the image to GHCR.

## Context

- DocPlatform currently manages MySQL schema via a hand-applied `schema.sql` (`spring.sql.init.mode: never`) plus "run once" ALTER statements recorded in the changelog. The file has drifted: `tenants` is missing `concurrent_job_limit`.
- ShopHub (reference project) has Flyway with `V1__init.sql`, a multi-stage Dockerfile, and a CI pipeline with service containers + a GHCR publish job.
- DocPlatform's pom already includes Testcontainers (mysql, mongodb, kafka) as unused test dependencies.
- `pom.xml` compiles at Java 17 while CI and CLAUDE.md state Java 21.

## Decisions (user-approved)

1. **CI scope:** full parity — real-MySQL integration test + GHCR publish job.
2. **Dockerfile scope:** backend only (multi-stage), same pattern as ShopHub.
3. **Integration test mechanism:** **Testcontainers**, not GitHub Actions service containers. The test starts its own throwaway MySQL container; works identically locally and in CI (Docker is preinstalled on `ubuntu-latest`). Chosen over service containers because only MySQL is needed and the test stays self-contained.

## Design

### 1. Flyway migrations

- Add `flyway-core` and `flyway-mysql` dependencies (versions managed by Spring Boot parent).
- Create `src/main/resources/db/migration/V1__init.sql` containing the full current schema (5 tables: `tenants`, `users`, `report_schedules`, `audit_logs`, `report_assignments`), fixing the drift:
  - `tenants.concurrent_job_limit INT NOT NULL DEFAULT 3` (present in the `Tenant` entity and production DB, missing from schema.sql)
  - `report_schedules.created_by BIGINT NOT NULL DEFAULT 0` (already in schema.sql, keep)
- Delete `schema.sql`; remove the `spring.sql.init` block from `application.yml`.
- Configure `spring.flyway.baseline-on-migrate: true` so the existing hand-migrated local `docplatform` database is baselined untouched; a fresh database gets V1 applied automatically.
- Future schema changes are `V2__...`, `V3__...` files — no more manual ALTERs in the changelog.

### 2. Dockerfile (backend, multi-stage)

Same pattern as ShopHub's:

- Stage 1: `maven:3.9-eclipse-temurin-21` — copy `pom.xml`, `mvn dependency:go-offline` (cached layer), copy `src`, `mvn package -DskipTests`.
- Stage 2: `eclipse-temurin:21-jre` — copy `doc-platform-*.jar` as `app.jar`, `EXPOSE 8080`, `ENTRYPOINT ["java", "-jar", "app.jar"]`.

Rider: align `pom.xml` to Java 21 (`java.version`, compiler source/target).

### 3. CI/CD (`.github/workflows/ci.yml`)

- `backend` job: unchanged steps (`mvn test`); the new Testcontainers integration test runs as part of it — no service containers needed.
- `frontend` job: unchanged (`npm ci && npm run build`).
- New `publish` job:
  - `needs: [backend, frontend]`
  - `if: github.ref == 'refs/heads/main'` (pushes only, not PRs)
  - `permissions: contents: read, packages: write`
  - Log into GHCR with `${{ secrets.GITHUB_TOKEN }}`, build the Dockerfile via `docker/build-push-action`, push `ghcr.io/<owner>/docplatform:latest` and `:<sha>`.

### 4. Integration test: `UserMapperIT`

- Uses Testcontainers MySQL (`@Testcontainers` + `MySQLContainer`, deps already in pom) with dynamic datasource properties.
- Flyway runs `V1__init.sql` against the container on context start — the test doubles as migration validation.
- Test body: set `TenantContextHolder` explicitly, insert a `User` through the real `UserMapper`, read it back, assert the tenant plugin scopes the query correctly. Clear the holder afterwards.
- Context: lightest Spring slice that brings up MyBatis-Plus + Flyway + the tenant interceptor (`MyBatisPlusConfig`). If a slice annotation proves awkward, a `@SpringBootTest` with Mongo/Kafka/Redis/MinIO auto-configs excluded is acceptable — implementation plan decides.
- Requires Docker locally to run; CI's `ubuntu-latest` has it preinstalled.

### 5. Repo hygiene

- Add root `.gitignore`: `target/`, `.idea/`, `*.iml`, `.superpowers/`, OS junk. (Currently absent — build artifacts are one `git add .` away from the public repo.)

## Error handling / risks

- **Existing local DB:** `baseline-on-migrate: true` is the guard; Flyway records a baseline row and never re-runs V1 there.
- **Publish job failures** don't affect test results; image push only happens after both test jobs pass on main.
- **Docker absent locally:** the integration test fails with a Testcontainers connection error; documented as a prerequisite.

## Testing

- Existing unit test suite must stay green.
- New `UserMapperIT` passes locally (with Docker running) and in CI.
- Fresh-database boot applies V1 cleanly; existing database boots with baseline.
- `docker build .` produces a runnable image.
- CI on main runs backend + frontend + publish; image appears in GHCR.
