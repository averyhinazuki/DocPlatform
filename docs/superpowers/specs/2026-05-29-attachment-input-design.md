---
name: attachment-input
description: Add "Attach Data File" button to ReportsView; uploaded CSV/Excel parsed on backend and merged into report params before Kafka job fires
metadata:
  type: spec
  status: approved
  date: 2026-05-29
---

# Attachment Input — Design Spec

**Date:** 2026-05-29
**Status:** Approved

---

## Problem

Users currently fill in report params manually via text inputs. For real workloads they already have the data in a spreadsheet or CSV export. There is no way to upload that file and have its values injected into the report.

---

## Solution

Add an "Attach Data File" button next to Generate Report. User uploads a `.csv` or `.xlsx` file whose header row matches template variable names and first data row provides values. Backend parses the file, merges values into `params`, and passes them into the existing Kafka pipeline.

---

## Architecture

**Format:** `POST /api/reports/generate` changes from `application/json` to `multipart/form-data`. Two parts:
- `request` — JSON string (Spring `@RequestPart` deserializes automatically when part has `Content-Type: application/json`)
- `file` — optional `MultipartFile` (`.csv` or `.xlsx`)

**Parsing:** New `AttachmentParserService` detects format by filename extension and routes to OpenCSV (CSV) or Apache POI (Excel). Reads header row → variable names, first data row → values. Returns `Map<String, Object>`.

**Merging:** File values are the base; `req.params()` (manually entered form values) overlay on top. Manual entries win on collision.

**Frontend:** Always sends `multipart/form-data`. The `request` part is a JSON `Blob` with `application/json` content type; the `file` part is omitted when no file is selected.

---

## Backend Design

### New: `AttachmentParserService`

**File:** `src/main/java/com/example/docplatform/service/AttachmentParserService.java`

```
parse(MultipartFile file) → Map<String, Object>
  - filename ends with ".csv"  → parseCsv(file)
  - filename ends with ".xlsx" → parseExcel(file)
  - otherwise                  → throw IllegalArgumentException("Unsupported file type")

parseCsv(file):
  - OpenCSV CSVReader, read all rows
  - row[0] = headers, row[1] = values
  - zip into Map<String, Object>
  - throw IllegalArgumentException if < 2 rows

parseExcel(file):
  - Apache POI XSSFWorkbook, first sheet
  - row 0 = headers (string cell values), row 1 = values (all read as strings)
  - zip into Map<String, Object>
  - throw IllegalArgumentException if < 2 rows
```

### Modified: `ReportController`

- Add `@Autowired AttachmentParserService attachmentParser`
- Change endpoint signature:

```
@PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
generate(
  @RequestPart("request") @Valid ReportRequest req,
  @RequestPart(value = "file", required = false) MultipartFile file,
  @AuthenticationPrincipal TenantUserDetails user
)
```

- When `file` is non-null and non-empty: parse → build `mergedParams` (file base + req.params() overlay) → reconstruct `ReportRequest` with merged params
- When `file` is null/empty: use `req` unchanged
- Return `400` with message body on `IllegalArgumentException` from parser

---

## Frontend Design

### Modified: `frontend/src/api/reports.js`

Update `generateReport` to accept an optional `file` argument:

```js
export const generateReport = (payload, file = null) => {
  const fd = new FormData()
  fd.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  if (file) fd.append('file', file)
  return api.post('/reports/generate', fd)
}
```

### Modified: `frontend/src/views/ReportsView.vue`

**New refs:**
```js
const fileInput = ref(null)
const attachedFile = ref(null)
```

**New methods:**
```js
function onFileChange(e) { attachedFile.value = e.target.files[0] ?? null }
function clearFile() { attachedFile.value = null; fileInput.value.value = '' }
```

**Template — between Recipients and Generate button:**
```html
<div class="form-group">
  <label>Data File <span class="hint">(.csv or .xlsx — header row must match variable names)</span></label>
  <input ref="fileInput" type="file" accept=".csv,.xlsx" style="display:none" @change="onFileChange" />
  <div v-if="attachedFile" class="file-chip">
    <span>{{ attachedFile.name }}</span>
    <button type="button" class="chip-clear" @click="clearFile">×</button>
  </div>
  <button v-else type="button" class="btn btn-ghost btn-sm" @click="fileInput.click()">
    Attach Data File
  </button>
</div>
```

**Submit:** pass `attachedFile.value` to `generateReport(payload, attachedFile.value)`. Clear `attachedFile` and reset `fileInput` after success.

**New styles:**
```css
.file-chip {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 12px; border-radius: 20px;
  background: var(--bg); border: 1px solid var(--border); font-size: 13px;
}
.chip-clear {
  background: none; border: none; cursor: pointer;
  font-size: 16px; color: var(--text-2); line-height: 1;
  padding: 0;
}
```

---

## Data Flow

```
User uploads file
  → ReportsView sends FormData (request JSON + file)
    → ReportController receives multipart
      → AttachmentParserService.parse() → Map<String,Object>
        → merge with req.params() (manual entries win)
          → ReportService.requestReport() (unchanged)
            → Kafka event with merged params
              → PdfReportGenerator / ExcelReportGenerator injects params into template
```

---

## Error Handling

| Error | Response |
|---|---|
| Unsupported file extension | 400 `Unsupported file type. Use .csv or .xlsx` |
| File has < 2 rows | 400 `File must have a header row and at least one data row` |
| Malformed CSV | 400 `Failed to parse CSV file` |
| Corrupt/invalid Excel | 400 `Failed to parse Excel file` |

---

## Files Changed

**Create (backend):**
- `src/main/java/com/example/docplatform/service/AttachmentParserService.java`

**Modify (backend):**
- `src/main/java/com/example/docplatform/controller/ReportController.java`

**Modify (frontend):**
- `frontend/src/api/reports.js`
- `frontend/src/views/ReportsView.vue`

**Create (test):**
- `src/test/java/com/example/docplatform/service/AttachmentParserServiceTest.java`

---

## Non-Goals

- Multi-row data (only first data row used)
- `.xls` (old Excel format) — `.xlsx` only
- Storing the attachment file in MinIO — parsed and discarded
- Frontend file validation beyond accept attribute
