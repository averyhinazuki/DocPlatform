# CI Pipeline — Design Spec
**Date:** 2026-06-04
**Status:** Approved

---

## 1. Overview

A GitHub Actions CI pipeline that runs automatically on every push and pull request to `main`. Two jobs run in parallel: one verifies the backend (Maven tests), one verifies the frontend (Vite build). A green checkmark badge on the README signals automated quality checks to recruiters and reviewers.

CD (deployment) is out of scope for this phase.

---

## 2. Trigger

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

Fires on every push to `main` and every PR targeting `main`.

---

## 3. Jobs

### 3.1 backend

Runs on `ubuntu-latest`.

| Step | Command | Notes |
|------|---------|-------|
| Checkout | `actions/checkout@v4` | |
| Set up Java 21 | `actions/setup-java@v4` | Temurin distribution |
| Cache Maven deps | `actions/cache@v4` | Key: `~/.m2`, keyed on `pom.xml` hash |
| Run tests | `mvn test` | Uses Mockito mocks — no real services needed |

### 3.2 frontend

Runs on `ubuntu-latest` in parallel with `backend`.

| Step | Command | Notes |
|------|---------|-------|
| Checkout | `actions/checkout@v4` | |
| Set up Node 20 | `actions/setup-node@v4` | |
| Cache node_modules | `actions/cache@v4` | Key: `frontend/package-lock.json` hash |
| Install deps | `npm install` | Run from `frontend/` directory |
| Build | `npm run build` | Catches broken imports, missing components, syntax errors |

---

## 4. Workflow file location

`.github/workflows/ci.yml` — GitHub Actions discovers workflows from this directory automatically.

---

## 5. Result

- Yellow spinner on commit while running
- Green ✓ or red ✗ per job, visible on commits and PRs
- README badge: `![CI](https://github.com/<user>/DocPlatform/actions/workflows/ci.yml/badge.svg)`

---

## 6. Out of Scope

- CD (deployment to a server) — deferred
- Frontend linting / ESLint — no linter configured yet
- Integration tests — backend tests use mocks only
- Branch protection rules — can be added after CI is proven stable
