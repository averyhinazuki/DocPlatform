# Report Preview Feature — Design Spec
**Date:** 2026-05-29

## Overview

Users receive an in-app notification when a report is ready. Clicking the notification navigates to the Files page with the document pre-selected, showing an inline preview (PDF) or a format fallback (Excel/CSV), plus a Download button. The Files page also replaces the manual ID lookup with a full document list.

---

## Architecture

### Data Layer

**`Notification` document** — add nullable `String documentId` field. No migration needed (MongoDB; existing docs get null implicitly).

**`InAppNotificationService.send()`** — signature changes from `(tenantId, recipients, message)` to `(tenantId, recipients, message, documentId)`. `documentId` is nullable; non-report notifications pass null.

**`NotificationConsumer`** — passes `event.documentId()` as the fourth argument to `inAppService.send()`.

### New Endpoint

```
GET /api/files
```
Returns `List<DocumentSummary>` for the caller's tenant, newest first. Uses the existing `findByTenantIdOrderByGeneratedAtDesc` repo query.

**`DocumentSummary` DTO record:**
```java
record DocumentSummary(String id, FileFormat fileFormat, ReportStatus status, LocalDateTime generatedAt, Long scheduleId) {}
```

**`GET /api/files/{id}/url`** — unchanged.

---

## Backend Components

| File | Change |
|------|--------|
| `document/Notification.java` | Add `private String documentId;` |
| `notification/InAppNotificationService.java` | Add `documentId` param to `send()` |
| `kafka/consumer/NotificationConsumer.java` | Pass `event.documentId()` to `inAppService.send()` |
| `dto/file/DocumentSummary.java` | New record DTO |
| `service/FileService.java` | Add `listByTenant(tenantId)` → maps repo results to `DocumentSummary` |
| `controller/FileController.java` | Add `GET /api/files` endpoint |

---

## Frontend Components

### `stores/notifications.js`
Include `documentId` in the notification objects stored in the Pinia store (it comes through the existing API response automatically once the backend field is added).

### `components/NotificationBell.vue`
- Notification items with a non-null `documentId` become clickable.
- On click: `router.push('/files?docId=' + n.documentId)`, close dropdown.
- Items without `documentId` remain plain text (no change to other notification types).

### `api/files.js`
Add `listDocuments()` → `GET /api/files`. Existing `getDownloadUrl(id)` unchanged.

### `views/FilesView.vue` — Full redesign

**Layout:** two-column split panel.

**Left panel** (fixed width ~280px, scrollable):
- Fetches document list on mount via `listDocuments()`.
- Each row: format badge (PDF / EXCEL / CSV), status chip, relative date.
- Clicking a row selects it (highlighted state).

**Right panel** (fills remaining width):
- Empty state when nothing is selected: "Select a report to preview."
- When a document is selected:
  1. Calls `getDownloadUrl(id)` to fetch presigned URL (5-min validity).
  2. **Download button** rendered above the preview area, always visible.
  3. **PDF**: `<iframe :src="presignedUrl">` — browser renders natively.
  4. **Excel / CSV**: "Preview not available for this format — use the download button above."
  5. Loading state: spinner in right panel while URL is being fetched.

**On mount with `?docId=` query param:**
1. List loads.
2. Row matching `docId` is auto-selected.
3. Right panel opens and fetches URL immediately — no user interaction required.

---

## Error Handling

- Document not found or access denied → show error message in right panel, do not crash list.
- Presigned URL fetch fails → show "Could not load preview." error message. Download button is hidden (no URL to point it at).
- Empty document list → show "No reports yet." in left panel.

---

## Out of Scope

- Marking notifications as read on click (existing behaviour unchanged).
- Pagination of the document list (render all, newest first).
- Preview for Excel/CSV formats (deferred — show fallback message).
- Report naming / title (documents have no human name field today).
