# Exactly-Once Report Delivery — Design Spec

**Date:** 2026-06-03
**Status:** Approved

---

## Problem

`ReportJobConsumer` has a failure window between `storageService.upload()` and the final `documentRepository.save()` (status=COMPLETED). If the JVM crashes in that window, Kafka retries the message and the consumer regenerates the report — producing a duplicate file in MinIO and wasting compute.

```
storageService.upload()     ← MinIO write succeeds
  ↓  ← crash here
documentRepository.save()   ← COMPLETED + objectKey never persisted
  ↓
Kafka retries → report generated again → duplicate file in MinIO
```

---

## Approach

**Two-phase MongoDB write** with idempotency checks at the top of `consume()`.

- After `storageService.upload()` succeeds, immediately persist `minioObjectKey` + `minioBucket` to MongoDB while status stays `IN_PROGRESS` (Phase 1 save).
- On any Kafka retry, check `doc.minioObjectKey` before doing any generation work. If set, the file already exists in MinIO — skip regeneration, call `completeAndPublish()` only.

No Redis keys or extra infrastructure. MongoDB is already the source of truth for `GeneratedDocument`; keeping `minioObjectKey` there is natural and debuggable.

---

## Data Flow

```
consume(event)
  │
  ├─ doc.status == COMPLETED?
  │     → return (silent, no quota release — already released in prior run)
  │
  ├─ doc.minioObjectKey != null?
  │     → load template
  │     → completeAndPublish(doc, event, template.getName())
  │     → quotaService.release(tenantId)
  │     → return
  │
  └─ Normal path:
       set IN_PROGRESS → save
       load template + generator
       build params (merge contentOverride if present)
       generate content bytes
       upload to MinIO → objectKey
       ── Phase 1 save: doc.minioObjectKey + doc.minioBucket (status still IN_PROGRESS)
       ── completeAndPublish(doc, event, template.getName())
       catch: set FAILED → save
       finally: quotaService.release(tenantId)
```

### `completeAndPublish()` (private helper)

Shared by both the skip path and the normal path:

```
doc.status = COMPLETED
doc.note = event.note()
doc.deliveredAt = now()
documentRepository.save(doc)
producer.publishCompleted(ReportCompletedEvent(..., doc.minioObjectKey, ...))
```

---

## Failure Scenarios

| Crash point | Doc state on retry | Outcome |
|---|---|---|
| Before MinIO upload | `IN_PROGRESS`, `minioObjectKey=null` | Normal path — report generated fresh |
| After MinIO upload, before Phase 1 save | `IN_PROGRESS`, `minioObjectKey=null` | Normal path — duplicate MinIO object possible (window is microseconds; acceptable) |
| After Phase 1 save, before Phase 2 | `IN_PROGRESS`, `minioObjectKey` set | Skip path — `completeAndPublish()` only, no re-generation |
| After Phase 2 COMPLETED save, before Kafka offset commit | `COMPLETED` | Silent skip — quota not released (TTL safety valve covers any stuck counter) |

---

## Files Changed

| File | Change |
|---|---|
| `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java` | Add two early-exit checks; Phase 1 save after upload; extract `completeAndPublish()` |
| `src/test/java/com/example/docplatform/kafka/consumer/ReportJobConsumerTest.java` | Create — 3 new tests |
| `docs/superpowers/plans/CHANGELOG.md` | Add entry |

---

## Tests

Three test cases in `ReportJobConsumerTest`:

1. **`skipPath_republishesCompletedEventWhenMinioKeyAlreadySet`** — doc pre-loaded with `minioObjectKey`; assert generator is never called, `publishCompleted` fires, quota is released.
2. **`skipPath_silentlyExitsWhenAlreadyCompleted`** — doc status is `COMPLETED`; assert no generator call, no quota release.
3. **`normalPath_savesObjectKeyBeforeMarkingCompleted`** — happy path; assert `documentRepository.save()` is called with `minioObjectKey` set before the COMPLETED save (verify call order via `InOrder`).

---

## Out of Scope

- Duplicate notification suppression on Kafka redelivery after full success (pre-existing at-least-once behaviour; covered by TTL safety valve).
- Deduplication of MinIO objects in the sub-millisecond window between upload and Phase 1 save.
- Changes to `ReportService`, `ReportController`, or any frontend code.
