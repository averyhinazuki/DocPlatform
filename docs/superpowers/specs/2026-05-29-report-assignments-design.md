# Report Assignments Design

## Goal

Admin can assign a report task to a user (template + guidance note). The user sees it on their dashboard, clicks "Generate Report", fills in params, and submits. The assignment auto-completes on submit. Admin can track status of all assignments.

## Data Model

New MySQL table added to `schema.sql`:

```sql
CREATE TABLE report_assignments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT NOT NULL,
  created_by   BIGINT NOT NULL,
  assignee_id  BIGINT NOT NULL,
  template_id  VARCHAR(100) NOT NULL,
  notes        TEXT,
  status       ENUM('PENDING','COMPLETED') DEFAULT 'PENDING',
  document_id  VARCHAR(100),
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME
);
```

- `created_by` — admin's user id
- `assignee_id` — user being assigned
- `template_id` — MongoDB ObjectId of the ReportTemplate
- `notes` — admin's guidance message to the user
- `document_id` — MongoDB GeneratedDocument id, filled when user submits
- `status` — one-way transition: PENDING → COMPLETED

## API

### New endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/assignments` | ADMIN | Create assignment |
| `GET` | `/api/assignments` | ADMIN | List all assignments in tenant |
| `GET` | `/api/assignments/mine` | USER | List caller's PENDING assignments |

**POST /api/assignments request body:**
```json
{ "assigneeId": 2, "templateId": "abc123", "notes": "Use Q1 2026 figures" }
```

**GET /api/assignments response:**
```json
[{
  "id": 1, "assigneeUsername": "averyuser", "templateName": "Sales Report",
  "notes": "Use Q1 2026 figures", "status": "PENDING",
  "createdAt": "2026-05-29T10:00:00", "completedAt": null, "documentId": null
}]
```

**GET /api/assignments/mine response:**
```json
[{
  "id": 1, "templateId": "abc123", "templateName": "Sales Report",
  "notes": "Use Q1 2026 figures", "createdAt": "2026-05-29T10:00:00"
}]
```

### Modified endpoint

**POST /api/reports/generate** — `ReportRequest` gains an optional `assignmentId` field:
```json
{ "scheduleId": 1, "templateId": "abc123", "format": "CSV", "params": {}, "recipients": ["averyadmin"], "assignmentId": 1 }
```

When `assignmentId` is non-null, `ReportController` marks the assignment `COMPLETED` with `document_id` immediately after queuing the report (synchronous, before returning the response). No Kafka changes needed.

## Backend Components

### New files
- `entity/ReportAssignment.java` — MyBatis-Plus entity
- `mapper/ReportAssignmentMapper.java` — extends BaseMapper
- `dto/assignment/AssignmentRequest.java` — `assigneeId`, `templateId`, `notes`
- `dto/assignment/AssignmentResponse.java` — full status view for admin
- `dto/assignment/MyAssignmentResponse.java` — pending view for assignee
- `service/AssignmentService.java` — create, listByTenant, listMine, complete
- `controller/AssignmentController.java` — the four endpoints above

### Modified files
- `dto/report/ReportRequest.java` — add `Long assignmentId` (nullable)
- `controller/ReportController.java` — after queuing, if `assignmentId != null` call `assignmentService.complete(assignmentId, documentId)`
- `schema.sql` — add `report_assignments` table

## Frontend Components

### New files
- `api/assignments.js` — `getMyAssignments()`, `createAssignment(data)`, `listAssignments()`

### Modified files

**`DashboardView.vue`**
- On mount, fetch `GET /api/assignments/mine`
- Render a "My Assignments" card above notifications
- Each item shows: template name, admin's note, "Generate Report" button
- Button navigates to `/reports?assignmentId=X&templateId=Y`
- Empty state: "No pending assignments"

**`ReportsView.vue`**
- On mount, read `assignmentId` and `templateId` from `$route.query`
- If present: auto-select the matching template (lock the dropdown — `disabled`), show admin's note as an info banner above the form, include `assignmentId` in the submit payload
- If absent: normal flow, no changes

**`AdminView.vue`**
- New "Assignments" section at the bottom
- Create form: user picker (dropdown from `GET /api/users`), template picker (dropdown from `GET /api/templates`), notes textarea
- Assignments table: columns — Assignee, Template, Notes, Status (badge), Created, Completed

## Flow

```
Admin fills form in AdminView → POST /api/assignments
  → assignee sees card on Dashboard (15s poll or page refresh)
    → clicks "Generate Report" → navigates to /reports?assignmentId=1&templateId=abc123
      → template auto-selected, admin's note shown as banner
        → user fills params, selects format, clicks Generate
          → POST /api/reports/generate { ..., assignmentId: 1 }
            → ReportController queues report + marks assignment COMPLETED
              → admin refreshes AdminView → assignment shows COMPLETED badge
              → admin gets in-app notification when report is ready (existing flow)
```

## Constraints

- Only admins can create or list all assignments (`@PreAuthorize("hasRole('ADMIN')")`)
- Assignments are tenant-scoped — `tenant_id` set from caller's session on create; all queries filter by tenant
- `complete()` checks that the assignment belongs to the caller's tenant before updating
- `assignmentId` in `ReportRequest` is nullable — existing report generation flow unchanged
