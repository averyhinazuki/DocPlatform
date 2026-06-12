# DocPlatform — Feature Backlog

## 1. Tenant-level concurrency quota ✅ DONE (2026-06-03)
Redis atomic counter per tenant capping concurrent report jobs. Requests beyond the limit are queued or rejected with a clear error. Prevents one tenant from flooding the Kafka queue and starving others. Provable with a load test — gives the project a concrete engineering story.

**Implemented:** `concurrent_job_limit` column on `tenants` table (default 3). `QuotaService` wraps a Redisson `RAtomicLong` keyed `tenant:{id}:running` — INCR on request, DECR in `ReportJobConsumer` finally block. Exceeding the limit returns HTTP 429 ("Concurrent report limit reached. Upgrade your plan."). 3600s TTL set on first increment as crash safety valve. 10 new tests across `QuotaServiceTest`, `TenantServiceTest`, and `ReportControllerTest`.

## 2. Exactly-once report delivery ✅ DONE (2026-06-03)
Closes the gap where a consumer crash after MinIO write but before MongoDB update causes a duplicate report.

**Implemented:** Two-phase MongoDB write in `ReportJobConsumer`. After MinIO upload, `minioObjectKey` is immediately persisted (status stays `IN_PROGRESS`). On Kafka retry, if `minioObjectKey != null` → skip regeneration, call `completeAndPublish()` with existing key. If `status == COMPLETED` → silent exit, no quota release. 3 new tests in `ReportJobConsumerTest` covering both skip paths and normal-path save ordering.

## 3. Real-time report status via SSE + Redis pub/sub ✅ DONE (2026-06-04)
Replace the 15-second NotificationBell polling with a WebSocket push using Spring's `SimpMessagingTemplate` or a Redisson `RTopic` subscriber. Smaller impact than #1 and #2 but removes a known design weakness and is easy to demo.

**Implemented:** Fixed RTopic topic key from tenant-scoped to user-scoped (`notifications:{tenantId}:{userId}`). `InAppNotificationService` now publishes a JSON payload (id, message, note, documentId) via Jackson. New `SseController` subscribes an RTopic listener per connected user and streams events via Spring `SseEmitter` (5-min timeout, cleanup on completion/timeout/error). Frontend `NotificationBell.vue` opens a native `EventSource` on mount and drops the `setInterval` poll; `notifications.js` store gains `connect()` / `disconnect()` — on SSE error, a single `fetch()` resyncs state. 3 new tests in `SseControllerTest`, updated `InAppNotificationServiceTest` (both packages).

## 4. README rewrite
The committed README has every markdown character backslash-escaped (`\#`, `\*\*`) so GitHub renders it as literal garbage — the first thing a reviewer sees. It's also only ~35 lines. Rewrite with: clean markdown, architecture overview/diagram, feature list, local setup instructions (Docker Compose, Flyway, env vars), and screenshots. Highest impact-to-effort item; no running environment needed.

## 5. Smoke-test the new Mac environment ✅ DONE (2026-06-13)
Boot backend + frontend against the freshly imported local MySQL (`docplatform` DB, Flyway `baseline-on-migrate` should handle the existing schema) plus the Docker-hosted infra (Kafka, Redis, MongoDB, MinIO). Confirms the Windows→Mac migration is complete; prerequisite for all dev work below.

**Done:** App boots end-to-end (login → generate report → SSE notification → file preview). Full test suite green (64 tests). Two environment fixes were needed: `JAVA_HOME` pinned to JDK 21 (Homebrew Maven defaults to its bundled JDK 26, breaking Lombok) and Testcontainers upgraded 1.21.3 → 2.0.5 (Docker Engine 29 rejects the 1.x client's API version — see changelog).

## 6. Secret externalization ✅ DONE (2026-06-13)
Move `password: 123456` and `minioadmin` credentials out of `application.yml` into environment variables with sane defaults for local dev. Quick, and a public repo with hardcoded credentials looks unprofessional to reviewers.

**Implemented:** `${DB_USERNAME:root}`, `${DB_PASSWORD:123456}`, `${MINIO_ENDPOINT:http://localhost:9000}`, `${MINIO_ACCESS_KEY:minioadmin}`, `${MINIO_SECRET_KEY:minioadmin}` in `application.yml`; env var table added to README. Boot-verified with defaults.

## 7. Kafka retry + DLT on ReportJobConsumer
A failed report job currently has no recovery path. Add retry with backoff on a dedicated retry topic and a dead-letter topic for exhausted messages, mirroring the ShopHub design. Real functional gap and a strong interview talking point ("what happens when a report job fails?").

## 8. Pagination on document lists
Document/report list endpoints return everything. Add pagination (MyBatis-Plus `Page`) and wire the frontend lists to it. Small but expected in any real app.

## 9. Performance evidence: quota load test + exactly-once crash demo
JMeter run with two tenants — one flooding report requests, proving clean 429s for the busy tenant and no starvation for the other. Separately, a crash-recovery demo: kill the consumer between MinIO write and Mongo update, restart, show exactly one report and no orphan. Produces the measurable numbers the resume bullets need.

## 10. Resume: fill the [Second Project] slot
Draft 4–5 DocPlatform bullets in ShopHub's style for `~/Desktop/resume.html`, leading with what ShopHub doesn't have: multi-tenancy + per-tenant quotas, exactly-once delivery (a step past ShopHub's at-least-once + idempotency), real-time SSE push, full-stack breadth (Vue 3, MongoDB, MinIO, Testcontainers). Use the numbers from #9.
