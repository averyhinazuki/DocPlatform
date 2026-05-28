# DocPlatform Changelog

---

## [Unreleased] — 2026-05-29

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
