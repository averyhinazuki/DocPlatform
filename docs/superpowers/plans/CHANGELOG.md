# DocPlatform Changelog

---

## 2026-05-29 — Rich Text Editor for No-Parameter PDF Templates

**Feature:** Admins can now compose static PDF content using a Rich Text Editor (Quill, standard toolbar) when creating a template with no variables. When a user generates a PDF report from such a template, the editor is pre-filled with the admin's content and the user can edit it before submitting. The edited HTML travels as `contentOverride` through the Kafka pipeline and is used directly by `PdfReportGenerator`, bypassing Thymeleaf.

**Toolbar:** Bold · Italic · Underline · H1 · H2 · Blockquote · Horizontal rule · Bullet list · Numbered list

**Backend files modified:**
- `src/main/java/com/example/docplatform/dto/report/ReportRequest.java` — added `contentOverride`
- `src/main/java/com/example/docplatform/kafka/event/ReportRequestedEvent.java` — added `contentOverride`
- `src/main/java/com/example/docplatform/service/ReportService.java` — passes `contentOverride` into event
- `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java` — injects `"__content"` into params when present
- `src/main/java/com/example/docplatform/report/generator/PdfReportGenerator.java` — checks `"__content"` first, bypasses Thymeleaf when set
- `src/main/java/com/example/docplatform/scheduler/ReportScheduler.java` — null for new field
- `src/main/java/com/example/docplatform/controller/ReportController.java` — passes `contentOverride` through merge path
- `src/main/java/com/example/docplatform/controller/TemplateController.java` — added `GET /api/templates/{id}`

**Backend files created:**
- `src/main/java/com/example/docplatform/dto/template/TemplateDetailResponse.java`

**Frontend files created:**
- `frontend/src/components/RteEditor.vue` — reusable Quill editor with standard toolbar
- `frontend/src/utils/htmlTemplate.js` — `wrapHtml()` and `extractBody()` helpers

**Frontend files modified:**
- `frontend/src/api/templates.js` — added `getTemplate(id)`
- `frontend/src/views/TemplatesView.vue` — shows RteEditor when variables empty
- `frontend/src/views/ReportsView.vue` — shows pre-filled RteEditor for no-variable PDF

**Tests modified:**
- `src/test/java/com/example/docplatform/service/ReportServiceTest.java` — updated 9-arg `ReportRequest` constructors

---

## 2026-05-29 — Fix: CSV/Excel generators ignore rows list, show only first row

**Bug:** `CsvReportGenerator` and `ExcelReportGenerator` used `params.getOrDefault(column, "")` — top-level key lookup only. After the multi-row parser change, actual data lives in `params["rows"]` (a list), while top-level keys hold only the first row. PDF worked because Thymeleaf iterates `${rows}` directly. CSV/Excel bypassed the list entirely, so output was either empty or first-row-only.

**Fix:** Both generators now check for a `rows` key first. When present, they iterate over every row in the list and write one output row per entry. When absent (no file uploaded, manual params only), they fall back to the original single-row top-level-key path.

**Backend files modified:**
- `src/main/java/com/example/docplatform/report/generator/CsvReportGenerator.java`
- `src/main/java/com/example/docplatform/report/generator/ExcelReportGenerator.java`

---

## 2026-05-29 — Multi-row data file support

**Feature:** Attached `.csv` / `.xlsx` files now parse ALL data rows, not just the first. The parser returns a `rows` key (`List<Map<String, Object>>`) alongside the top-level first-row keys (backward compat). The generated PDF template uses `th:each="row : ${rows}"` so every row appears in the report table. Numeric integer cells (e.g. `1` stored as `1.0` in Excel) are now formatted without the decimal suffix.

**Backend files modified:**
- `src/main/java/com/example/docplatform/service/AttachmentParserService.java` — parse all rows; expose as `rows` list + top-level first-row keys; new `cellValue()` helper for clean numeric formatting

**Frontend files modified:**
- `frontend/src/views/TemplatesView.vue` — `buildPdfTemplate` now generates a column-header table with `th:each="row : ${rows}"` instead of the old field:value key-value layout

**Tests modified:**
- `src/test/java/com/example/docplatform/service/AttachmentParserServiceTest.java` — updated existing tests; added multi-row CSV, multi-row Excel, and integer-cell-format tests

---

## 2026-05-29 — Template Delete

**Feature:** Admins can delete a template from the Available Templates table. A ✕ button appears in each row (admin-only). Clicking it shows a confirmation prompt, then calls `DELETE /api/templates/{id}` and refreshes the list. The backend guards against cross-tenant deletion.

**Backend files modified:**
- `src/main/java/com/example/docplatform/controller/TemplateController.java` — added `DELETE /api/templates/{id}` (admin-only, tenant-scoped)

**Frontend files modified:**
- `frontend/src/api/templates.js` — added `deleteTemplate(id)`
- `frontend/src/views/TemplatesView.vue` — remove button + `remove()` handler

---

## 2026-05-29 — Fix: PDF generation fails with blank form + Excel data file

**Bug:** Generating a PDF report with an attached `.xlsx` data file while leaving all form fields blank caused two failures:

1. **Merge logic inversion** — `ReportController` used `putAll` to overlay form params on top of file params, so blank form strings (`""`) overrode the non-empty Excel values. Result: all template variables were empty strings.
2. **"Premature end of file" from openhtmltopdf** — `PdfReportGenerator` passed the result of `templateEngine.process(thymeleafTemplate, ctx)` directly to `builder.withHtmlContent()`. When `thymeleafTemplate` is `""` (empty string, stored in MongoDB for templates created without HTML content), Thymeleaf's `StringTemplateResolver` returns `""`, and openhtmltopdf's XML parser throws `SAXParseException: Premature end of file at line 1, col 1`.

**Fix:**
- `ReportController` — only merge a form param into the file-param map when the form value is non-blank (blank form = "use the file value").
- `PdfReportGenerator` — guard against null/blank `thymeleafTemplate` and throw a clear `IllegalStateException` instead of the cryptic XML parse error.
- `TemplateRequest` — added `@NotBlank` to `thymeleafTemplate` so the API rejects template creation without HTML content at validation time.

**Backend files modified:**
- `src/main/java/com/example/docplatform/controller/ReportController.java`
- `src/main/java/com/example/docplatform/report/generator/PdfReportGenerator.java`
- `src/main/java/com/example/docplatform/dto/template/TemplateRequest.java`

---

## 2026-05-29 — Report Note

**Feature:** Optional note field on report generation form. Note travels through the full Kafka pipeline (`ReportRequest` → `ReportRequestedEvent` → `GeneratedDocument` → `ReportCompletedEvent` → `Notification`) and surfaces as italic secondary text in the notification bell, Dashboard notifications card, and Files preview panel.

**Backend files modified:**
- `dto/report/ReportRequest.java` — added `String note`
- `kafka/event/ReportRequestedEvent.java` — added `String note`
- `kafka/event/ReportCompletedEvent.java` — added `String note`
- `service/ReportService.java` — passes `req.note()` into event
- `document/GeneratedDocument.java` — added `String note`
- `kafka/consumer/ReportJobConsumer.java` — saves note to doc; passes to completed event
- `document/Notification.java` — added `String note`
- `notification/InAppNotificationService.java` — added `note` param to `send()`
- `kafka/consumer/NotificationConsumer.java` — passes `event.note()` to `inAppService.send()`
- `dto/file/DocumentSummary.java` — added `String note`
- `service/FileService.java` — maps `doc.getNote()` into summary
- `controller/ReportController.java` — passes `req.note()` in reconstructed request
- `scheduler/ReportScheduler.java` — passes `null` for note

**Tests fixed:**
- `ReportServiceTest.java`, `InAppNotificationServiceTest.java` (×2) — updated call signatures

**Frontend files modified:**
- `frontend/src/views/ReportsView.vue` — note textarea (optional)
- `frontend/src/views/FilesView.vue` — note shown in preview toolbar
- `frontend/src/components/NotificationBell.vue` — note shown below message
- `frontend/src/views/DashboardView.vue` — note shown below message

---

## 2026-05-29 — Attach Data File (Report Generation)

**Feature:** Users can attach a `.csv` or `.xlsx` file when generating a report. The backend parses the header row + first data row into a `Map<String, Object>` and merges it into `params` before the Kafka job fires. Manually entered form values override file values. Unsupported extensions and malformed files return 400.

**Backend files created:**
- `src/main/java/com/example/docplatform/service/AttachmentParserService.java` — parses CSV (OpenCSV) and Excel (Apache POI) attachments
- `src/test/java/com/example/docplatform/service/AttachmentParserServiceTest.java` — 5 tests (CSV, Excel, error cases)

**Backend files modified:**
- `src/main/java/com/example/docplatform/controller/ReportController.java` — changed to multipart/form-data, injects AttachmentParserService, merges file params

**Frontend files modified:**
- `frontend/src/api/reports.js` — always sends FormData; appends file part when present
- `frontend/src/views/ReportsView.vue` — file input, chip display, clear button, wired into submit

---

## 2026-05-29 — Dynamic Params Form

**Feature:** The raw `Params (JSON)` textarea in ReportsView is replaced by a dynamic list of labeled text inputs — one per variable declared on the selected template. Switching templates resets the inputs. Templates with no variables show nothing. Works in both one-off and assignment modes.

**Frontend files modified:**
- `frontend/src/views/ReportsView.vue` — replaced `paramsRaw` textarea with `paramsForm` reactive object and per-variable inputs

---

## 2026-05-29 — Merge Schedules into Assignments

**Feature:** Schedules functionality (create schedule, view schedule list) is merged into the Assignments page. The Schedules nav link is removed. All users see the Assignments page. Admin-only sections (Assign Report Task, All Assignments) remain gated by role. `/schedules` redirects to `/assignments` for backward-compatibility.

**Frontend files modified:**
- `frontend/src/views/AssignmentsView.vue` — merged schedule sections; role-gated admin sections; form reset after success; null-safe status binding
- `frontend/src/components/NavBar.vue` — removed Schedules link; removed admin guard on Assignments link
- `frontend/src/router/index.js` — added `/schedules` redirect; removed SchedulesView import

---

## 2026-05-29 — Report Preview & Document List

**Feature:** Clicking a report-ready notification navigates to the Files page with the document auto-selected and previewed. PDF reports render inline; Excel/CSV show a format-fallback message. The Files page is redesigned as a split panel (document list on the left, preview on the right) replacing the manual ID lookup.

**Backend files modified:**
- `src/main/java/com/example/docplatform/document/Notification.java` — added `documentId` field
- `src/main/java/com/example/docplatform/notification/InAppNotificationService.java` — added `documentId` param to `send()`
- `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java` — passes `event.documentId()` to inAppService
- `src/main/java/com/example/docplatform/service/FileService.java` — added `listByTenant()`
- `src/main/java/com/example/docplatform/controller/FileController.java` — added `GET /api/files`

**Backend files created:**
- `src/main/java/com/example/docplatform/dto/file/DocumentSummary.java`

**Tests created:**
- `src/test/java/com/example/docplatform/service/InAppNotificationServiceTest.java`
- `src/test/java/com/example/docplatform/service/FileServiceTest.java` (expanded with listByTenant tests)

**Frontend files modified:**
- `frontend/src/api/files.js` — added `listDocuments()`
- `frontend/src/components/NotificationBell.vue` — clickable report notifications
- `frontend/src/views/FilesView.vue` — full split-panel redesign

---

## 2026-05-29 — Report Assignments

**Feature:** Admin can assign a report task to a user (template + guidance note). User sees it on Dashboard, clicks Generate Report, fills in params, submits. Assignment auto-completes on submit. Admin can track status.

**Backend files created:**
- `src/main/resources/schema.sql` (modified — report_assignments table)
- `src/main/java/com/example/docplatform/enums/AssignmentStatus.java`
- `src/main/java/com/example/docplatform/entity/ReportAssignment.java`
- `src/main/java/com/example/docplatform/mapper/ReportAssignmentMapper.java`
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentRequest.java`
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentResponse.java`
- `src/main/java/com/example/docplatform/dto/assignment/MyAssignmentResponse.java`
- `src/main/java/com/example/docplatform/service/AssignmentService.java`
- `src/main/java/com/example/docplatform/controller/AssignmentController.java`

**Backend files modified:**
- `src/main/java/com/example/docplatform/dto/report/ReportRequest.java` (scheduleId nullable, assignmentId added)
- `src/main/java/com/example/docplatform/service/ReportService.java` (null scheduleId handling)
- `src/main/java/com/example/docplatform/scheduler/ReportScheduler.java` (updated to 7-arg ReportRequest)
- `src/main/java/com/example/docplatform/controller/ReportController.java` (calls assignmentService.complete)

**Frontend files created:**
- `frontend/src/api/assignments.js`

**Frontend files modified:**
- `frontend/src/views/DashboardView.vue` (My Assignments card)
- `frontend/src/views/ReportsView.vue` (assignment-aware mode)
- `frontend/src/views/AdminView.vue` (Assignments panel)

**Tests created:**
- `src/test/java/com/example/docplatform/service/AssignmentServiceTest.java`

**Tests modified:**
- `src/test/java/com/example/docplatform/service/ReportServiceTest.java` (updated ReportRequest constructor to 7 args)

---

## [Unreleased] — 2026-05-29

### Report Assignments — AssignmentService
Business logic layer for the report_assignments feature. Implements create, listByTenant, listMine, and complete operations. One disambiguation fix was applied to the test: changed `when(assignmentMapper.insert(any()))` to `when(assignmentMapper.insert(any(ReportAssignment.class)))` to resolve ambiguity between the two BaseMapper `insert` overloads.

Files added:
- `src/main/java/com/example/docplatform/service/AssignmentService.java` — @Service with create, listByTenant, listMine, complete

Files modified:
- `src/test/java/com/example/docplatform/service/AssignmentServiceTest.java` — expanded from 1 test to 5 (create, listMine, complete, complete throws for wrong tenant)

Test results: 26 tests, 0 failures, 0 errors (all tests pass).

---

### Report Assignments — enum, entity, mapper, DTO records
Java layer for Report Assignments feature. AssignmentStatus enum (PENDING, COMPLETED), ReportAssignment entity with MyBatis-Plus annotations, ReportAssignmentMapper (CRUD), and three DTO records: AssignmentRequest (assigneeId, templateId, notes), AssignmentResponse (for admin view), MyAssignmentResponse (for assignee view).

Files added:
- `src/main/java/com/example/docplatform/enums/AssignmentStatus.java` — enum with PENDING, COMPLETED
- `src/main/java/com/example/docplatform/entity/ReportAssignment.java` — entity with @TableName("report_assignments"), @TableId(AUTO)
- `src/main/java/com/example/docplatform/mapper/ReportAssignmentMapper.java` — extends BaseMapper<ReportAssignment>
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentRequest.java` — record with assigneeId, templateId, notes
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentResponse.java` — record with id, assigneeUsername, templateName, notes, status, createdAt, completedAt, documentId
- `src/main/java/com/example/docplatform/dto/assignment/MyAssignmentResponse.java` — record with id, templateId, templateName, notes, createdAt
- `src/test/java/com/example/docplatform/service/AssignmentServiceTest.java` — test verifying AssignmentStatus has PENDING and COMPLETED

Test results: All tests pass (1 test, 0 failures, 0 errors).

---

### Report Assignments table (schema)
Added `report_assignments` table to store assignments created by admins for users to generate specific reports. Fields: id, tenant_id, created_by, assignee_id, template_id, notes, status (PENDING/COMPLETED), document_id, created_at, completed_at.

Files edited:
- `src/main/resources/schema.sql` — added CREATE TABLE IF NOT EXISTS report_assignments

---

### Template management — list & create
Admins can create report templates through the UI; all users can browse templates and pick them when creating schedules or generating reports. Report Type auto-fills from the selected template.

Files added:
- `src/main/java/com/example/docplatform/dto/template/TemplateRequest.java`
- `src/main/java/com/example/docplatform/dto/template/TemplateResponse.java`
- `src/main/java/com/example/docplatform/controller/TemplateController.java` — `GET /api/templates` (all users), `POST /api/templates` (admin only)
- `frontend/src/api/templates.js`
- `frontend/src/views/TemplatesView.vue`

Files edited:
- `frontend/src/router/index.js` — added `/templates` route
- `frontend/src/components/NavBar.vue` — added Templates nav link
- `frontend/src/views/SchedulesView.vue` — Template ID + Report Type replaced by template dropdown; variables shown as params hint
- `frontend/src/views/ReportsView.vue` — same

---

### Fix: generatePresignedUrl crashes NotificationConsumer before in-app notification
Root cause: `generatePresignedUrl` (throws Exception) was called outside any try-catch in `NotificationConsumer`. MinIO failure crashed the consumer before `inAppService.send()` could run.

Files edited:
- `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java` — moved `generatePresignedUrl` inside the email try-catch; in-app notification now always runs

---

### Fix: in-app notification never delivered when email fails
Root cause: `NotificationConsumer` called `emailService` before `inAppService` in a single try-catch. No MailHog SMTP server in docker-compose caused email to throw, blocking the in-app save entirely, and Kafka retried the message indefinitely.
Additionally, `NotificationBell` only fetched on mount with no polling, so even saved notifications were never shown.

Files edited:
- `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java` — email failure is now caught independently (warning logged); in-app notification always runs
- `frontend/src/components/NotificationBell.vue` — added 15-second polling so the bell refreshes while the admin is on the page

---

### Session restore on page refresh
Page refresh no longer drops the user back to login when their session cookie is still valid.

Files edited:
- `frontend/src/stores/auth.js` — added `ensureInitialized()`: calls `GET /api/auth/me` once on first navigation (result cached as a promise), populates `username` + `role`; `login()` and `logout()` resolve the promise immediately so no redundant fetch occurs
- `frontend/src/router/index.js` — router guard is now `async` and awaits `ensureInitialized()` before checking auth state

---

### Recipient picker from tenant users (Reports & Schedules)
Recipients are now selected from existing users in the tenant via checkboxes, replacing the free-text comma-separated email input.

Files edited:
- `src/main/java/com/example/docplatform/service/UserService.java` — added `listByTenant(tenantId)`
- `src/main/java/com/example/docplatform/controller/AdminController.java` — added `GET /api/users` returning `[{id, username}]` for the caller's tenant
- `frontend/src/api/users.js` — new file, `listUsers()`
- `frontend/src/views/SchedulesView.vue` — replaced text input with checkbox user picker
- `frontend/src/views/ReportsView.vue` — replaced text input with checkbox user picker

---

### Role-based Admin nav visibility
Admin link in the navbar is now hidden from regular users and only shown when `user.role === 'ADMIN'`.

Files edited:
- `src/main/java/com/example/docplatform/controller/AuthController.java` — added `GET /api/auth/me` endpoint returning `{ username, role }`
- `frontend/src/api/auth.js` — added `me()` call
- `frontend/src/stores/auth.js` — added `role` ref, populated from `/auth/me` after login
- `frontend/src/components/NavBar.vue` — added `v-if="authStore.role === 'ADMIN'"` on Admin link

---

### Cron expression builder (Schedules)
Replaced raw cron text input with a friendly builder. Frequency dropdown (Hourly / Daily / Weekly / Monthly / Custom) with contextual hour and day selectors. Generated 6-field Spring cron expression shown as preview. Custom fallback for power users.

Files edited:
- `frontend/src/views/SchedulesView.vue`

---

## Backend (original)

### Auth (session-based)
Login, register, logout with Spring Session + Redis. First registrant in a tenant is auto-promoted to ADMIN.

Files: `AuthController.java`, `AuthService.java`, `SecurityConfig.java`, `TenantUserDetails.java`, `UserDetailsServiceImpl.java`

### Multi-tenancy
Every request is scoped to a tenant via a filter that sets `TenantContextHolder`.

Files: `TenantContextHolder.java`, `TenantContextFilter.java`, `MyBatisPlusConfig.java`

### Entities & Mappers
MyBatis-Plus entities for `User`, `Tenant`, `Schedule`; enums `Role`, `FileFormat`, `ReportStatus`.

Files: `enums/`, `entity/`, `mapper/`

### MongoDB Documents & Repositories
`GeneratedDocument` and `ReportJob` stored in MongoDB.

Files: `document/`, `repository/`

### MinIO File Storage
Upload and presigned-URL download scoped by tenant.

Files: `DocumentStorageService.java`

### Report Pipeline
Kafka event → `ReportJobConsumer` → `ReportService` (Redisson distributed lock) → PDF / Excel / CSV generation.

Files: `ReportService.java`, `ReportController.java`, `ReportJobConsumer.java`, `generator/PdfReportGenerator.java`, `generator/ExcelReportGenerator.java`, `generator/CsvReportGenerator.java`

### Schedule Management
6-field Spring cron (`seconds minutes hours day-of-month month day-of-week`). `CronExpression.parse()` used for next-run computation.

Files: `ScheduleService.java`, `ScheduleController.java`, `ReportScheduler.java`

### Notifications
Email (Spring Mail + Thymeleaf) and in-app (Redisson RTopic + 15s polling). No MailHog in docker-compose — email is best-effort; in-app always runs.

Files: `InAppNotificationService.java`, `EmailNotificationService.java`, `NotificationController.java`, `NotificationConsumer.java`

### Tenant & Role Management API
`POST /api/tenants` (public), `GET /api/tenants` (admin), `PATCH /api/users/{id}/role` (admin).

Files: `TenantController.java`, `AdminController.java`

---

## Frontend (Vue 3 + Pinia, original)

### Auth store & API
Login/logout/session-restore in Pinia. `ensureInitialized()` called by router guard on every navigation.

Files: `stores/auth.js`, `api/auth.js`

### Views
- `LoginView.vue`, `RegisterView.vue` — auth forms
- `DashboardView.vue` — summary + notifications
- `SchedulesView.vue` — schedule list + cron builder + template picker + recipient checkboxes
- `ReportsView.vue` — one-off report generation, template picker, recipient checkboxes
- `FilesView.vue` — file upload and download
- `AdminView.vue` — tenant list and role management (admin only)
- `TemplatesView.vue` — template list (all users); create form (admin only)
