# Infra Parity (Flyway, Dockerfile, CI/CD) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Flyway-versioned MySQL migrations, a multi-stage Dockerfile, a GHCR publish job in CI, and a Testcontainers integration test that proves the migration and tenant plugin work against real MySQL.

**Architecture:** Flyway owns the MySQL schema via `db/migration/V1__init.sql` (replacing the hand-applied, drifted `schema.sql`); `baseline-on-migrate: true` protects the existing local DB. A `@MybatisPlusTest` slice + Testcontainers MySQL runs Flyway and exercises real mappers — self-contained locally and in CI (no service containers). CI gains a `publish` job that builds the multi-stage Dockerfile and pushes to GHCR on pushes to `main`.

**Tech Stack:** Spring Boot 3.4.1, Flyway (`flyway-core` + `flyway-mysql`), MyBatis-Plus 3.5.9 (`mybatis-plus-spring-boot3-starter-test`), Testcontainers MySQL (already in pom), Docker multi-stage build, GitHub Actions + GHCR.

**Spec:** `docs/superpowers/specs/2026-06-11-infra-parity-design.md`

**Prerequisites for executing locally:** Docker Desktop running (Testcontainers and `docker build` need it).

**Key context an executor must know:**
- The `users`, `tenants`, and `report_schedules` tables are in `TENANT_EXEMPT` (`src/main/java/com/example/docplatform/config/MyBatisPlusConfig.java:21`) — the tenant plugin does NOT filter them. `report_assignments` and `audit_logs` ARE filtered. The integration test therefore uses `ReportAssignmentMapper` to prove tenant isolation and `UserMapper` for plain schema validation.
- The current `src/main/resources/schema.sql` has drifted: `tenants` is missing `concurrent_job_limit` (the `Tenant` entity has the field; the prod DB got it via a manual ALTER). `V1__init.sql` must include it.
- Test naming matters: Maven Surefire (which `mvn test` and CI run) only picks up `*Test` classes, NOT `*IT` classes. The integration test must be named `MapperIntegrationTest`.
- Work directly on `main` (this repo's existing convention — solo project, all prior work committed to main).

---

### Task 1: Root .gitignore

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
# Build output
target/

# IDE
.idea/
*.iml
.vscode/

# Claude/agent scratch
.superpowers/

# OS
.DS_Store
Thumbs.db

# Logs
*.log
```

- [ ] **Step 2: Verify it takes effect**

Run: `git status --short`
Expected: `target/`, `.idea/`, and `.superpowers/` no longer appear as untracked; `.gitignore` appears as `??`.

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: add root .gitignore (target, .idea, scratch dirs)"
```

---

### Task 2: Align pom.xml to Java 21

**Files:**
- Modify: `pom.xml:16` (`<java.version>`) and `pom.xml:95` (compiler `<source>`/`<target>`)

- [ ] **Step 1: Update Java version properties**

In `pom.xml`, change:

```xml
<java.version>17</java.version>
```
to
```xml
<java.version>21</java.version>
```

and in the `maven-compiler-plugin` configuration, change:

```xml
<source>17</source><target>17</target>
```
to
```xml
<source>21</source><target>21</target>
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (CI and CLAUDE.md already assume Java 21; local JDK is 21).

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: align Java version to 21 (matches CI and docs)"
```

---

### Task 3: Failing integration test (Testcontainers MySQL + MyBatis-Plus slice)

This is the TDD red step for Flyway: the test boots Flyway against a throwaway MySQL container, but no `db/migration` directory exists yet, so the context fails to start. Task 4 makes it green.

**Files:**
- Modify: `pom.xml` (add 3 test/runtime dependencies)
- Create: `src/test/java/com/example/docplatform/mapper/MapperIntegrationTest.java`

- [ ] **Step 1: Add dependencies to `pom.xml`**

Inside `<dependencies>`, after the existing `mybatis-plus-jsqlparser` dependency, add:

```xml
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
```

(No `<version>` — the Spring Boot 3.4.1 parent manages Flyway's version. `flyway-mysql` is required for MySQL 8 support in Flyway 10.)

After the existing `spring-security-test` test dependency, add:

```xml
<dependency>
  <groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot3-starter-test</artifactId>
  <version>${mybatis-plus.version}</version><scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write the integration test**

Create `src/test/java/com/example/docplatform/mapper/MapperIntegrationTest.java`:

```java
package com.example.docplatform.mapper;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.example.docplatform.config.MyBatisPlusConfig;
import com.example.docplatform.entity.ReportAssignment;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.AssignmentStatus;
import com.example.docplatform.enums.Role;
import com.example.docplatform.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a real MySQL via Testcontainers, lets Flyway apply db/migration,
 * and exercises real mappers with the tenant interceptor active.
 *
 * Named *Test (not *IT) so Maven Surefire runs it as part of `mvn test` / CI.
 * Requires Docker.
 */
@MybatisPlusTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(MyBatisPlusConfig.class)
class MapperIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    UserMapper userMapper;

    @Autowired
    ReportAssignmentMapper assignmentMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void flywaySchemaSupportsUserCrud() {
        User user = new User();
        user.setTenantId(1L);
        user.setUsername("it-user");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        userMapper.insert(user);

        User found = userMapper.selectById(user.getId());
        assertThat(found.getUsername()).isEqualTo("it-user");
        assertThat(found.getTenantId()).isEqualTo(1L);
    }

    @Test
    void tenantPluginIsolatesReportAssignments() {
        TenantContextHolder.setTenantId(1L);
        assignmentMapper.insert(assignment(1L));

        TenantContextHolder.setTenantId(2L);
        assignmentMapper.insert(assignment(2L));

        TenantContextHolder.setTenantId(1L);
        assertThat(assignmentMapper.selectList(null))
                .hasSize(1)
                .allMatch(a -> a.getTenantId().equals(1L));
    }

    @Test
    void missingTenantContextFallsBackToTenantZero() {
        TenantContextHolder.setTenantId(1L);
        assignmentMapper.insert(assignment(1L));

        TenantContextHolder.clear();
        assertThat(assignmentMapper.selectList(null)).isEmpty();
    }

    private ReportAssignment assignment(Long tenantId) {
        ReportAssignment a = new ReportAssignment();
        a.setTenantId(tenantId);
        a.setCreatedBy(10L);
        a.setAssigneeId(20L);
        a.setTemplateId("tpl-1");
        a.setNotes("integration test");
        a.setStatus(AssignmentStatus.PENDING);
        return a;
    }
}
```

Notes for the executor:
- `@MybatisPlusTest` is a slice: it auto-configures the datasource + mappers only. It does NOT pick up `@Configuration` classes, hence the explicit `@Import(MyBatisPlusConfig.class)` (tenant interceptor) and `@ImportAutoConfiguration(FlywayAutoConfiguration.class)` (guarantees Flyway runs regardless of slice defaults).
- The slice is transactional and rolls back after each test — tests are isolated from each other.
- `MySQLContainer` is `static`, so one container serves all three tests.

- [ ] **Step 3: Run the test — expect failure (no migrations yet)**

Run: `mvn test -Dtest=MapperIntegrationTest`
Expected: FAIL — Spring context startup error from Flyway, complaining that the configured migration location `classpath:db/migration` does not exist (message like "Unable to resolve location classpath:db/migration"). If instead you see a Docker connection error, Docker Desktop is not running — start it and retry.

Do NOT commit yet — Task 4 makes this green; commit happens there.

---

### Task 4: Flyway migration V1 + config (green step)

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`
- Modify: `src/main/resources/application.yml` (replace `sql.init` block with `flyway` block)
- Delete: `src/main/resources/schema.sql`

- [ ] **Step 1: Create `src/main/resources/db/migration/V1__init.sql`**

This is the current production schema — including `tenants.concurrent_job_limit`, which had drifted out of the old `schema.sql` (the entity `Tenant.concurrentJobLimit` and the real DB both have it).

```sql
CREATE TABLE tenants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  plan VARCHAR(50) DEFAULT 'FREE',
  concurrent_job_limit INT NOT NULL DEFAULT 3,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  username VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','USER') DEFAULT 'USER',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE report_schedules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  cron_expr VARCHAR(100) NOT NULL,
  report_type VARCHAR(50) NOT NULL,
  format ENUM('PDF','EXCEL','CSV') NOT NULL,
  template_id VARCHAR(100) NOT NULL,
  recipients JSON,
  params JSON,
  created_by BIGINT NOT NULL DEFAULT 0,
  status ENUM('ACTIVE','PAUSED') DEFAULT 'ACTIVE',
  last_run_at DATETIME,
  next_run_at DATETIME
);

CREATE TABLE audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT,
  action VARCHAR(100) NOT NULL,
  resource VARCHAR(255),
  detail TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE report_assignments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT NOT NULL,
  created_by   BIGINT NOT NULL,
  assignee_id  BIGINT NOT NULL,
  template_id  VARCHAR(100) NOT NULL,
  notes        TEXT,
  status       ENUM('PENDING','COMPLETED') DEFAULT 'PENDING',
  document_id  VARCHAR(100),
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME,
  INDEX idx_ra_tenant (tenant_id),
  INDEX idx_ra_assignee_status (assignee_id, status)
);
```

(Plain `CREATE TABLE`, not `IF NOT EXISTS` — Flyway only runs V1 on empty schemas; the existing local DB is protected by baseline, see Step 2.)

- [ ] **Step 2: Update `src/main/resources/application.yml`**

Replace:

```yaml
  sql:
    init:
      mode: never
```

with:

```yaml
  flyway:
    baseline-on-migrate: true
```

(`baseline-on-migrate: true` — when Flyway meets the existing non-empty local `docplatform` database, it records a baseline at version 1 and does NOT run `V1__init.sql` there. A fresh/empty database gets V1 applied normally. All future changes go in `V2__*.sql` etc.)

- [ ] **Step 3: Delete the old schema file**

```bash
git rm src/main/resources/schema.sql
```

- [ ] **Step 4: Run the integration test — expect pass**

Run: `mvn test -Dtest=MapperIntegrationTest`
Expected: PASS, 3 tests. The log should show Flyway lines: `Migrating schema "test" to version "1 - init"` and `Successfully applied 1 migration`.

- [ ] **Step 5: Run the full suite**

Run: `mvn test`
Expected: BUILD SUCCESS, all existing unit tests + the 3 new integration tests pass.

- [ ] **Step 6: Commit (Tasks 3 + 4 together — red and green)**

```bash
git add pom.xml src/test/java/com/example/docplatform/mapper/MapperIntegrationTest.java src/main/resources/db/migration/V1__init.sql src/main/resources/application.yml
git commit -m "feat: Flyway migrations + Testcontainers mapper integration test

- V1__init.sql replaces hand-applied schema.sql (fixes concurrent_job_limit drift)
- baseline-on-migrate protects the existing local DB
- MapperIntegrationTest proves migration + tenant plugin against real MySQL"
```

(`git rm` already staged the schema.sql deletion.)

---

### Task 5: Multi-stage Dockerfile

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1: Create `Dockerfile` at repo root**

```dockerfile
# ── Stage 1: build ────────────────────────────────────────────────────────────
# Full Maven + JDK image. Used to compile and package, then thrown away.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy the dependency manifest first. Docker caches each layer separately, so
# as long as pom.xml doesn't change, the Maven download step is fully cached.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source and build. -DskipTests because CI already ran them.
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: run ──────────────────────────────────────────────────────────────
# Minimal JRE image — no compiler, no Maven, no source code.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy only the fat JAR produced by Stage 1.
COPY --from=build /app/target/doc-platform-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Verify the image builds**

Run: `docker build -t docplatform:local .`
Expected: both stages complete, final line `naming to docker.io/library/docplatform:local`. (First build takes several minutes downloading Maven deps; that layer is cached afterwards.) Booting the container isn't required — it needs the full MySQL/Mongo/Kafka/Redis/MinIO stack.

- [ ] **Step 3: Commit**

```bash
git add Dockerfile
git commit -m "build: multi-stage Dockerfile (maven build stage -> JRE runtime)"
```

---

### Task 6: CI publish job (GHCR)

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Replace `.github/workflows/ci.yml` with the full updated workflow**

The `backend` and `frontend` jobs are unchanged (the new integration test runs inside `mvn test` — `ubuntu-latest` has Docker preinstalled, so Testcontainers just works). The `publish` job is new.

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Run tests
        run: mvn -B test --no-transfer-progress

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: Build
        working-directory: frontend
        run: npm run build

  publish:
    name: Build & push Docker image
    runs-on: ubuntu-latest
    needs: [backend, frontend]            # only runs if both test jobs pass
    if: github.ref == 'refs/heads/main'   # only on pushes to main, not PRs

    permissions:
      contents: read
      packages: write                     # required to push to GHCR

    steps:
      - uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}  # auto-injected, no setup needed

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ghcr.io/${{ github.repository_owner }}/docplatform:latest
            ghcr.io/${{ github.repository_owner }}/docplatform:${{ github.sha }}
```

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: publish Docker image to GHCR on main after tests pass"
git push origin main
```

- [ ] **Step 3: Watch the CI run**

```bash
gh run list --limit 1
gh run watch <run-id-from-previous-command> --exit-status
```

Expected: `backend`, `frontend`, and `publish` all succeed. The backend job log shows the Testcontainers MySQL starting and the 3 `MapperIntegrationTest` tests passing.

- [ ] **Step 4: Verify the image landed in GHCR**

```bash
gh api /users/averyhinazuki/packages/container/docplatform/versions --jq '.[0].metadata.container.tags'
```

Expected: output contains `latest` and the commit SHA. (Alternatively check https://github.com/averyhinazuki?tab=packages.)

---

### Task 7: Changelog

**Files:**
- Modify: `docs/superpowers/plans/CHANGELOG.md`

- [ ] **Step 1: Add a new entry at the top of the changelog (below the `# DocPlatform Changelog` heading)**

```markdown
## 2026-06-11 — Infra Parity: Flyway, Dockerfile, GHCR publish

**Feature:** MySQL schema is now managed by Flyway. `V1__init.sql` replaces the hand-applied `schema.sql` and fixes its drift (`tenants.concurrent_job_limit` was missing). `baseline-on-migrate: true` protects the existing local DB; fresh databases are migrated automatically; future changes are `V2__*.sql` files. A new `MapperIntegrationTest` boots a Testcontainers MySQL, runs Flyway, and verifies real-mapper CRUD plus tenant-plugin isolation on `report_assignments` (including the tenant_id=0 fallback). A multi-stage Dockerfile (Maven build stage → JRE runtime) was added, and CI gained a `publish` job that pushes `ghcr.io/averyhinazuki/docplatform:{latest,sha}` after both test jobs pass on main. Also: root `.gitignore` added; pom aligned to Java 21.

**Files created:**
- `src/main/resources/db/migration/V1__init.sql` — full current schema (5 tables, drift fixed)
- `src/test/java/com/example/docplatform/mapper/MapperIntegrationTest.java` — 3 Testcontainers tests
- `Dockerfile` — multi-stage build
- `.gitignore` — root ignore file

**Files modified:**
- `pom.xml` — Java 21; flyway-core, flyway-mysql, mybatis-plus-spring-boot3-starter-test deps
- `src/main/resources/application.yml` — removed `sql.init`, added `spring.flyway.baseline-on-migrate`
- `.github/workflows/ci.yml` — added `publish` job (GHCR)

**Files removed:**
- `src/main/resources/schema.sql` — replaced by Flyway V1

---
```

- [ ] **Step 2: Commit and push**

```bash
git add docs/superpowers/plans/CHANGELOG.md
git commit -m "docs: changelog for infra parity (Flyway, Dockerfile, GHCR publish)"
git push origin main
```
