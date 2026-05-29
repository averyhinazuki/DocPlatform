---
name: report-note
description: Add optional note field to report generation; note flows through the Kafka pipeline and surfaces in Files page, notification bell, and Dashboard
metadata:
  type: spec
  status: approved
  date: 2026-05-29
---

# Report Note — Design Spec

**Date:** 2026-05-29
**Status:** Approved

---

## Problem

When generating a report there is no way to attach a contextual note (e.g. "Use Q1 figures, exclude returns"). The note should travel with the report through the pipeline and surface in all downstream views: the Files page, the notification bell, and the Dashboard notifications card.

---

## Solution

Add an optional `note` field to `ReportRequest`. Thread it through `ReportRequestedEvent` → `GeneratedDocument` → `ReportCompletedEvent` → `Notification`. Expose it in `DocumentSummary` and in frontend notification items.

---

## Data Flow

```
ReportRequest.note (nullable String)
  → ReportRequestedEvent.note
    → GeneratedDocument.note        (ReportJobConsumer saves it)
    → ReportCompletedEvent.note
      → Notification.note           (InAppNotificationService saves it)
```

---

## Backend Changes

### `ReportRequest` (record)
Add `String note` as the last field (nullable — no `@NotBlank`):
```java
public record ReportRequest(
    Long scheduleId,
    @NotBlank String reportType,
    @NotNull FileFormat format,
    @NotBlank String templateId,
    Map<String, Object> params,
    List<String> recipients,
    Long assignmentId,
    String note
) {}
```

### `ReportRequestedEvent` (record)
Add `String note` as the last field:
```java
public record ReportRequestedEvent(
    String documentId, Long tenantId, Long scheduleId,
    String reportType, String fileFormat, String templateId,
    Map<String, Object> params, List<String> recipients,
    String triggeredBy, String note
) {}
```

### `ReportService`
Pass `req.note()` when constructing `ReportRequestedEvent`:
```java
producer.publishRequest(new ReportRequestedEvent(
    doc.getId(), tenantId, req.scheduleId(),
    req.reportType(), req.format().name(),
    req.templateId(), req.params(), req.recipients(),
    username, req.note()
));
```

### `GeneratedDocument` (MongoDB document)
Add field:
```java
private String note;
```

### `ReportJobConsumer`
Before saving `doc` after generation succeeds, set note:
```java
doc.setNote(event.note());
```
Pass note into `ReportCompletedEvent`:
```java
producer.publishCompleted(new ReportCompletedEvent(
    doc.getId(), event.tenantId(), objectKey, "reports",
    event.fileFormat(), event.recipients(),
    template.getName(), event.triggeredBy(), event.note()));
```

### `ReportCompletedEvent` (record)
Add `String note` as the last field:
```java
public record ReportCompletedEvent(
    String documentId, Long tenantId,
    String minioObjectKey, String minioBucket,
    String fileFormat, List<String> recipients,
    String templateName, String triggeredBy, String note
) {}
```

### `Notification` (MongoDB document)
Add field:
```java
private String note;
```

### `InAppNotificationService`
Add `String note` param to `send()`:
```java
public void send(Long tenantId, List<String> recipientEmails, String message, String documentId, String note)
```
Set note on the saved `Notification`:
```java
n.setNote(note);
```

### `NotificationConsumer`
Pass `event.note()` to `inAppService.send()`:
```java
inAppService.send(event.tenantId(), event.recipients(), msg, event.documentId(), event.note());
```

### `DocumentSummary` (DTO record)
Add `String note`:
```java
public record DocumentSummary(
    String id, FileFormat fileFormat, ReportStatus status,
    LocalDateTime generatedAt, Long scheduleId, String note
) {}
```

### `FileService`
Map note when building `DocumentSummary`:
```java
new DocumentSummary(doc.getId(), doc.getFileFormat(), doc.getStatus(),
    doc.getGeneratedAt(), doc.getScheduleId(), doc.getNote())
```

---

## Frontend Changes

### `ReportsView.vue`
Add optional textarea below the Report Data section, above Recipients:
```html
<div class="form-group">
  <label>Note <span class="hint">(optional — travels with the report)</span></label>
  <textarea v-model="form.note" placeholder="e.g. Use Q1 figures, exclude returns" rows="2"></textarea>
</div>
```
`form.note` is already part of the `form` reactive object (add `note: ''` to its initialisation). It's included in `payload` automatically via `...form`.

### `FilesView.vue`
In the document detail panel, show note below the status/format info:
```html
<p v-if="selectedDoc.note" class="doc-note">{{ selectedDoc.note }}</p>
```
Style: `font-size: 13px; color: var(--text-2); font-style: italic; margin-top: 6px;`

### `NotificationBell.vue`
Below each notification message, render note if present:
```html
<p class="notif-note" v-if="n.note">{{ n.note }}</p>
```
Style: `font-size: 12px; color: var(--text-2); font-style: italic; margin-top: 2px;`

### `DashboardView.vue`
Same treatment in the Notifications card:
```html
<p class="notif-note" v-if="n.note">{{ n.note }}</p>
```

---

## Tests to Update

- `ReportServiceTest` — update `ReportRequest` constructor calls (8 args now, add `null` for note)
- `InAppNotificationServiceTest` — update `send()` call signature (add `null` for note)

---

## Existing Tests Affected (constructor arity)

Any test constructing `ReportRequestedEvent` or `ReportCompletedEvent` directly must add `null` as the last arg.

---

## Non-Goals

- Note is not editable after submission
- Note is not shown on the Admin Assignments view
- Note field on scheduled reports (scheduler uses null)

---

## Files Changed

**Modify (backend):**
- `src/main/java/com/example/docplatform/dto/report/ReportRequest.java`
- `src/main/java/com/example/docplatform/controller/ReportController.java` — pass `req.note()` as 8th arg when reconstructing `ReportRequest` for file-attach path
- `src/main/java/com/example/docplatform/kafka/event/ReportRequestedEvent.java`
- `src/main/java/com/example/docplatform/service/ReportService.java`
- `src/main/java/com/example/docplatform/document/GeneratedDocument.java`
- `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java`
- `src/main/java/com/example/docplatform/kafka/event/ReportCompletedEvent.java`
- `src/main/java/com/example/docplatform/document/Notification.java`
- `src/main/java/com/example/docplatform/notification/InAppNotificationService.java`
- `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java`
- `src/main/java/com/example/docplatform/dto/file/DocumentSummary.java`
- `src/main/java/com/example/docplatform/service/FileService.java`

**Modify (tests):**
- `src/test/java/com/example/docplatform/service/ReportServiceTest.java`
- `src/test/java/com/example/docplatform/service/InAppNotificationServiceTest.java`
- `src/test/java/com/example/docplatform/scheduler/ReportSchedulerTest.java`

**Modify (frontend):**
- `frontend/src/views/ReportsView.vue`
- `frontend/src/views/FilesView.vue`
- `frontend/src/components/NotificationBell.vue`
- `frontend/src/views/DashboardView.vue`
