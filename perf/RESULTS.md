# Performance & Correctness Evidence — 2026-06-13

Environment: single app instance (Spring Boot, local), Docker-hosted Kafka/Redis/MongoDB/MinIO,
local MySQL. Apple Silicon laptop. JMeter 5.6.3 headless. PDF report jobs (real openhtmltopdf
rendering per job). Two tenants, `concurrent_job_limit = 3` each.

Reproduce: `./perf/seed.sh && jmeter -n -t perf/quota-load-test.jmx -l results.jtl && python3 perf/analyze.py results.jtl`

## 1. Tenant concurrency quota under burst load

A "noisy" tenant fires a **200-request burst** (100 threads, 1s ramp) while a "quiet" tenant
submits 20 reports at a normal pace.

| Metric | Noisy tenant | Quiet tenant |
|---|---|---|
| Requests | 200 | 20 |
| Accepted (HTTP 202) | **3** (= its concurrency limit) | **19** |
| Rejected (HTTP 429) | **197**, p50 61 ms | 1 (briefly at its own limit) |
| Max admitted concurrency (Redis counter) | **3 — never exceeded** | 2 |
| End-to-end completion (request → report delivered) | p50 1.32 s | **p50 22 ms, max 714 ms — during the flood** |

- **No starvation:** the quota caps the noisy tenant's backlog at 3, so the shared Kafka
  consumer stays available — the quiet tenant's reports complete in milliseconds while the
  burst is being rejected.
- **Zero loss, zero duplicates:** 22 accepted = 22 COMPLETED documents; both Redis counters
  drain to 0 after the run (no leaked quota slots).

## 2. Exactly-once delivery (duplicate-event replay)

`./perf/replay-idempotency.sh` republishes the Kafka event of an already-COMPLETED report,
simulating at-least-once redelivery:

```
replaying event for doc=… tenant=39 (MinIO objects: 273, quota counter: 0)
after replay: MinIO objects 273 -> 273, status=COMPLETED, quota counter=0
PASS: duplicate event was a no-op (no new object, status intact, no quota drift)
```

## 3. Bugs found by this testing (both fixed)

1. **Quota rejections returned HTTP 500, not 429.** The `@RestControllerAdvice` catch-all
   `@ExceptionHandler(Exception.class)` takes precedence over `@ResponseStatus` on
   `TenantQuotaExceededException`; unit tests passed because standalone MockMvc doesn't
   register the advice. Fixed with an explicit 429 handler + regression test.
2. **A malformed Kafka message permanently wedged a partition.** With a bare
   `JsonDeserializer`, deserialization fails inside `poll()` and the error handler cannot
   process it — infinite redelivery loop (verified live). Fixed by wrapping with
   `ErrorHandlingDeserializer`, which routes poison pills through the normal error
   handling/DLT path; the consumer recovers and moves on.
