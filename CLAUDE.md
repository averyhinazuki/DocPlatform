# DocPlatform — Claude Instructions

## Source of Truth

All files in `docs/superpowers/plans/` are the source of truth for this project.

**At the start of every session, read all files in that directory before doing anything else.**

This includes:
- `CHANGELOG.md` — every feature built and bug fixed, with exact file paths
- Any `YYYY-MM-DD-*.md` implementation plans — approved designs to follow

Do not rely on memory, codebase exploration, or assumptions about what has been built. Read the plans directory first, then act.

## Workflow

- Teach mode: finish one task at a time, write a summary after each task completes, then wait for the user to say continue.
- Update `docs/superpowers/plans/CHANGELOG.md` after every task with what changed and which files were touched.

## Stack

- Backend: Java 21, Spring Boot 3, MyBatis-Plus (MySQL), Spring Data MongoDB, Spring Kafka, Redisson, MinIO, Spring Session (Redis)
- Frontend: Vue 3, Vite, Pinia, Vue Router, Axios
- Cron format: **6-field Spring cron** — `seconds minutes hours day-of-month month day-of-week`
