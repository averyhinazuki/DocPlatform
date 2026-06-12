# DocPlatform Frontend — Design Spec
**Date:** 2026-05-28
**Status:** Approved

---

## 1. Overview

A Vue 3 single-page application living inside `DocPlatform/frontend/`. Covers every backend API endpoint. Visual style: clean, minimal — Apple system font, white cards, `#007AFF` blue accent, generous whitespace. No UI component library.

---

## 2. Tech Stack

| Concern | Choice |
|---|---|
| Framework | Vue 3 (Composition API) |
| Build tool | Vite |
| Routing | Vue Router 4 |
| State | Pinia |
| HTTP | Axios (`withCredentials: true`) |
| Styling | Plain CSS (no library) |
| Dev proxy | Vite proxy `/api` → `http://localhost:8080` |

---

## 3. Pages

| Route | View | Access |
|---|---|---|
| `/login` | `LoginView.vue` | Public |
| `/register` | `RegisterView.vue` | Public |
| `/dashboard` | `DashboardView.vue` | Authenticated |
| `/schedules` | `SchedulesView.vue` | Authenticated |
| `/reports` | `ReportsView.vue` | Authenticated |
| `/files` | `FilesView.vue` | Authenticated |
| `/admin` | `AdminView.vue` | ADMIN only |

Unauthenticated users are redirected to `/login`. `/admin` redirects non-admins to `/dashboard`.

---

## 4. File Structure

```
frontend/
├── src/
│   ├── api/
│   │   ├── axios.js
│   │   ├── auth.js
│   │   ├── schedules.js
│   │   ├── reports.js
│   │   ├── files.js
│   │   ├── notifications.js
│   │   └── admin.js
│   ├── stores/
│   │   ├── auth.js
│   │   └── notifications.js
│   ├── views/
│   │   ├── LoginView.vue
│   │   ├── RegisterView.vue
│   │   ├── DashboardView.vue
│   │   ├── SchedulesView.vue
│   │   ├── ReportsView.vue
│   │   ├── FilesView.vue
│   │   └── AdminView.vue
│   ├── components/
│   │   ├── NavBar.vue
│   │   └── NotificationBell.vue
│   ├── router/index.js
│   ├── App.vue
│   └── main.js
├── vite.config.js
└── package.json
```

---

## 5. Component Details

### NavBar.vue
- Links to all pages; Admin link hidden for `USER` role
- `NotificationBell` component on the right showing unread badge count
- Logout button — calls `POST /api/auth/logout`, clears auth store, redirects to `/login`

### NotificationBell.vue
- Shows badge with count from notifications store
- On click: shows dropdown list of unread notification messages

### LoginView.vue
- Fields: `username`, `password`
- On submit: `POST /api/auth/login` → populate auth store → redirect to `/dashboard`

### RegisterView.vue
- Fields: `username`, `password`, `tenantSlug`
- On submit: `POST /api/auth/register` → redirect to `/login`

### DashboardView.vue
- Fetches and displays unread notifications list on mount
- "Mark all read" button → `POST /api/notifications/read-all` → clears list and badge

### SchedulesView.vue
- Table of existing schedules (`GET /api/schedules`) — shows name, format, cron, status, nextRunAt
- "New Schedule" form: name, cronExpr, reportType, format (PDF/EXCEL/CSV), templateId, recipients (comma-separated), params (JSON textarea)
- On submit: `POST /api/schedules` → refresh list

### ReportsView.vue
- Form: scheduleId, reportType, format, templateId, recipients, params (JSON textarea)
- On submit: `POST /api/reports/generate` → display returned `documentId` in a result box
- Note shown to user: "Report is generating in background. You'll be notified when ready."

### FilesView.vue
- Single input: `documentId`
- On submit: `GET /api/files/{documentId}/url` → display the presigned URL as a clickable download link

### AdminView.vue
- Tenants table: `GET /api/tenants` — shows id, name, slug, plan
- Role update form: userId input + role dropdown (ADMIN/USER) → `PATCH /api/users/{id}/role`

---

## 6. State

### auth store
```js
{ username: String, role: 'ADMIN' | 'USER', tenantId: Number }
```
- `login(req)` — calls API, sets state
- `logout()` — calls API, clears state
- `isAdmin` — computed, `role === 'ADMIN'`

### notifications store
```js
{ unread: Array }
```
- `badgeCount` — computed, `unread.length`
- `fetch()` — calls `GET /api/notifications`
- `markAllRead()` — calls `POST /api/notifications/read-all`, clears `unread`

---

## 7. API Layer

All files import from `api/axios.js`:
```js
// axios.js
import axios from 'axios'
export default axios.create({ baseURL: '/api', withCredentials: true })
```

Each domain file exports named async functions that call the instance and return `response.data`.

---

## 8. Routing & Guards

```js
// Every route except /login and /register:
if (!authStore.username) → redirect to /login

// /admin only:
if (authStore.role !== 'ADMIN') → redirect to /dashboard
```

---

## 9. Error Handling

Each view has a `error` ref. API calls are wrapped in try/catch — on failure, `error.value` is set to the error message. A `<p class="error-msg">` below each form displays it. No external toast or modal library.

---

## 10. Styling

- Font: `-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`
- Accent: `#007AFF`
- Background: `#f5f5f7`
- Cards: white, `border-radius: 12px`, `box-shadow: 0 1px 4px rgba(0,0,0,0.08)`
- Buttons: `#007AFF` fill, white text, `border-radius: 8px`
- Inputs: `border: 1px solid #d1d1d6`, `border-radius: 8px`
- Max content width: `960px`, centered

---

## 11. Vite Config

```js
// vite.config.js
server: {
  proxy: {
    '/api': 'http://localhost:8080'
  }
}
```
