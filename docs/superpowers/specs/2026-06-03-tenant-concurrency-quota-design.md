# Tenant-Level Concurrency Quota

**Date:** 2026-06-03  
**Status:** Approved

## Problem

The report pipeline uses a shared Kafka consumer pool. Any tenant can submit an unlimited number of report jobs simultaneously, flooding the Kafka queue and starving other tenants' jobs. One misbehaving tenant can monopolize the consumer for an extended period.

## Goal

Cap the number of concurrent report jobs any single tenant can have queued or in-flight at any moment. Requests beyond the cap are rejected with HTTP 429. The cap is configured per tenant and tied to their subscription plan — tenants cannot change it themselves.

## Non-Goals

- Per-tenant Kafka partitioning or fair-scheduling consumer (overkill for this project)
- Admin UI for editing limits (changed via plan upgrade, out of scope)
- Queueing rejected requests (report pipeline is already async; retry is the client's responsibility)

## Design

### Schema

Add one column to the `tenants` table:

```sql
ALTER TABLE tenants ADD COLUMN concurrent_job_limit INT NOT NULL DEFAULT 3;
```

Default of 3 means every existing tenant gets a limit of 3 concurrent jobs without any data migration.

### Redis Counter

```
Key:   tenant:{tenantId}:running
Type:  integer (via INCR / DECR)
TTL:   3600 seconds, set on the first INCR (when counter goes 0 → 1)
```

The TTL is a safety valve: if the consumer JVM crashes mid-job, the DECR never runs. The counter resets automatically within one hour, freeing the tenant's slots. The TTL is only set when the counter transitions from 0 to 1 — subsequent INCRs do not reset the TTL, so a continuous stream of jobs does not keep the key alive past the original expiry.

### Enforcement Point — `ReportController`

Before publishing to Kafka, `requestReport()` runs the quota check:

1. Load `tenant.concurrent_job_limit` from MySQL via `TenantService`.
2. `INCR tenant:{tenantId}:running` — atomic, no race condition.
3. If the result > limit: `DECR` (undo), return `HTTP 429` with body `{"error": "Concurrent report limit reached. Upgrade your plan."}`.
4. If the result == 1: set `EXPIRE tenant:{tenantId}:running 3600` (safety valve).
5. Otherwise: publish the `ReportRequestedEvent` to Kafka as normal.

### Release Point — `ReportJobConsumer`

The counter must be decremented after every job completion, regardless of outcome:

```java
try {
    // existing report generation logic
} finally {
    redissonClient.getAtomicLong("tenant:" + tenantId + ":running").decrementAndGet();
}
```

Using `finally` ensures failed jobs free their slot immediately, preventing quota leaks from error cases.

### Redis Unavailability

If Redis is down, the INCR call throws. The controller catches this, logs a warning, and **fails open** — the job proceeds without quota enforcement. Report generation remains available; quota is best-effort. This avoids Redis becoming a hard dependency for the core pipeline.

## Data Flow

```
User submits report
  → ReportController
      → load limit from MySQL
      → INCR Redis counter
      → counter > limit? → 429 (DECR to undo)
      → publish ReportRequestedEvent to Kafka
          → ReportJobConsumer
              → generate report (PDF / Excel / CSV)
              → [finally] DECR Redis counter
```

## How to Prove It Works

A load test submitting 10 concurrent requests from the same tenant should show:
- First N requests succeed (where N = `concurrent_job_limit`)
- Remaining requests return 429
- After a job finishes (consumer decrements), a new slot opens and the next submission succeeds

## Files to Change

| File | Change |
|------|--------|
| `src/main/resources/schema.sql` | Add `concurrent_job_limit` column to `tenants` |
| `src/main/java/.../entity/Tenant.java` | Add `Integer concurrentJobLimit` field |
| `src/main/java/.../service/TenantService.java` | Add `getLimit(tenantId)` |
| `src/main/java/.../controller/ReportController.java` | Quota check before Kafka publish |
| `src/main/java/.../scheduler/ReportJobConsumer.java` | `finally` DECR after job |
| `src/test/.../controller/ReportControllerTest.java` | 429 test + pass-through test |
