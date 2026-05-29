# Design: Merge Schedules into Assignments

**Date:** 2026-05-29  
**Status:** Approved

## Goal

Consolidate the Schedules and Assignments views into a single "Assignments" page accessible to all users. Remove the Schedules nav link. The schedule functionality is not removed — it moves into the merged view.

## Page Structure (`AssignmentsView.vue`)

Single scrollable page with four cards in order:

1. **Create Schedule** — visible to all users  
   Existing SchedulesView form: name, cron builder (hourly/daily/weekly/monthly/custom), template picker, format, recipient checkboxes, params JSON textarea.

2. **Your Schedules** — visible to all users  
   Existing schedule list table (name, cron, format, status, next run).

3. **Assign Report Task** — `v-if="authStore.role === 'ADMIN'"` only  
   Existing assignment create form: assignee picker, template picker, notes textarea.

4. **All Assignments** — `v-if="authStore.role === 'ADMIN'"` only  
   Existing assignments table: assignee, template, notes, status badge, created, completed.

`tenantUsers` and `templates` are fetched once on mount and shared by both the schedule form and the assignment form.

## Data / Script

Variable names are kept from their source views. One rename to avoid collision: the schedule create reactive object is renamed from `form` → `scheduleForm`. All other refs and functions remain as-is.

`onMounted` calls: `loadSchedules()`, `loadUsersAndTemplates()`, and — if admin — `loadAssignments()`. Since role is available from the auth store, `loadAssignments` is gated: `if (authStore.role === 'ADMIN') await loadAssignments()`.

## Router Changes

- `/assignments` → `AssignmentsView` (unchanged)
- `/schedules` → redirect to `/assignments` (new redirect entry, preserves existing bookmarks)

## NavBar Changes

- Remove `<RouterLink to="/schedules">Schedules</RouterLink>`
- Remove `v-if="authStore.role === 'ADMIN'"` from the Assignments link (all users see it)

## Files Touched

| File | Change |
|------|--------|
| `frontend/src/views/AssignmentsView.vue` | Merge schedule sections in; add auth store import for role check |
| `frontend/src/components/NavBar.vue` | Remove Schedules link; remove admin guard on Assignments link |
| `frontend/src/router/index.js` | Add `/schedules` redirect to `/assignments` |

`frontend/src/views/SchedulesView.vue` — left in place, no longer routed to (dead file, harmless).

## Non-Goals

- No backend changes
- No changes to the Schedules API or Assignment API
- No deletion of SchedulesView.vue
