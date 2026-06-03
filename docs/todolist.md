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
