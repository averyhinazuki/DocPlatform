# DocPlatform Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Vue 3 single-page application inside `DocPlatform/frontend/` that covers every backend API endpoint with a clean, minimal Apple-style UI.

**Architecture:** Vue 3 + Vite SPA with Vue Router for navigation, Pinia for auth and notification state, and Axios with `withCredentials: true` for session-cookie-based API calls. Vite's dev proxy forwards `/api` to `http://localhost:8080` so no CORS config is needed during development.

**Tech Stack:** Vue 3, Vite 5, Vue Router 4, Pinia 2, Axios 1.x

---

## File Map

**Create:**
- `frontend/package.json`
- `frontend/vite.config.js`
- `frontend/index.html`
- `frontend/src/main.js`
- `frontend/src/App.vue`
- `frontend/src/assets/main.css`
- `frontend/src/api/axios.js`
- `frontend/src/api/auth.js`
- `frontend/src/api/schedules.js`
- `frontend/src/api/reports.js`
- `frontend/src/api/files.js`
- `frontend/src/api/notifications.js`
- `frontend/src/api/admin.js`
- `frontend/src/stores/auth.js`
- `frontend/src/stores/notifications.js`
- `frontend/src/router/index.js`
- `frontend/src/components/NavBar.vue`
- `frontend/src/components/NotificationBell.vue`
- `frontend/src/views/LoginView.vue`
- `frontend/src/views/RegisterView.vue`
- `frontend/src/views/DashboardView.vue`
- `frontend/src/views/SchedulesView.vue`
- `frontend/src/views/ReportsView.vue`
- `frontend/src/views/FilesView.vue`
- `frontend/src/views/AdminView.vue`

---

## Task 1: Project Scaffold

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "docplatform-frontend",
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "axios": "^1.7.2",
    "pinia": "^2.1.7",
    "vue": "^3.4.29",
    "vue-router": "^4.3.3"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.5",
    "vite": "^5.3.1"
  }
}
```

- [ ] **Step 2: Create `frontend/vite.config.js`**

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
```

- [ ] **Step 3: Create `frontend/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>DocPlatform</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```

- [ ] **Step 4: Install dependencies**

```bash
cd frontend
npm install
```

Expected: `node_modules/` created, no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/package.json frontend/vite.config.js frontend/index.html
git commit -m "feat: scaffold Vue 3 frontend project"
```

---

## Task 2: Global CSS + App Entry

**Files:**
- Create: `frontend/src/assets/main.css`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`

- [ ] **Step 1: Create `frontend/src/assets/main.css`**

```css
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
  --accent: #007AFF;
  --bg: #f5f5f7;
  --card: #ffffff;
  --text: #1d1d1f;
  --text-2: #6e6e73;
  --border: #d1d1d6;
  --error: #ff3b30;
  --success: #34c759;
  --radius: 12px;
  --radius-sm: 8px;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: var(--bg);
  color: var(--text);
  font-size: 15px;
  line-height: 1.5;
}

.page {
  max-width: 960px;
  margin: 0 auto;
  padding: 40px 24px;
}

.card {
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  padding: 28px;
}

h1 { font-size: 28px; font-weight: 700; letter-spacing: -0.5px; margin-bottom: 24px; }
h2 { font-size: 18px; font-weight: 600; margin-bottom: 16px; }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: 10px 20px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn:hover { opacity: 0.85; }
.btn:disabled { opacity: 0.45; cursor: not-allowed; }

.btn-ghost {
  background: transparent;
  color: var(--accent);
  border: 1px solid var(--accent);
}

.form-group { margin-bottom: 16px; }
.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-2);
  margin-bottom: 6px;
}
.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  font-size: 15px;
  font-family: inherit;
  background: #fff;
  color: var(--text);
  outline: none;
  transition: border-color 0.15s;
}
.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus { border-color: var(--accent); }
.form-group textarea { resize: vertical; min-height: 80px; }

.error-msg { color: var(--error); font-size: 13px; margin-top: 12px; }
.success-msg { color: var(--success); font-size: 13px; margin-top: 12px; }
.empty-state { color: var(--text-2); font-size: 14px; padding: 16px 0; }

table { width: 100%; border-collapse: collapse; }
th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
td { padding: 12px; border-bottom: 1px solid var(--border); font-size: 14px; }
tr:last-child td { border-bottom: none; }
```

- [ ] **Step 2: Create `frontend/src/main.js`**

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

- [ ] **Step 3: Create `frontend/src/App.vue`**

```vue
<template>
  <NavBar v-if="authStore.username" />
  <RouterView />
</template>

<script setup>
import NavBar from './components/NavBar.vue'
import { useAuthStore } from './stores/auth'
const authStore = useAuthStore()
</script>
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/
git commit -m "feat: add global CSS, main entry, App.vue"
```

---

## Task 3: API Layer

**Files:**
- Create: `frontend/src/api/axios.js`
- Create: `frontend/src/api/auth.js`
- Create: `frontend/src/api/schedules.js`
- Create: `frontend/src/api/reports.js`
- Create: `frontend/src/api/files.js`
- Create: `frontend/src/api/notifications.js`
- Create: `frontend/src/api/admin.js`

- [ ] **Step 1: Create `frontend/src/api/axios.js`**

```js
import axios from 'axios'
export default axios.create({
  baseURL: '/api',
  withCredentials: true
})
```

- [ ] **Step 2: Create `frontend/src/api/auth.js`**

```js
import api from './axios'
export const login = (data) => api.post('/auth/login', data)
export const logout = () => api.post('/auth/logout')
export const register = (data) => api.post('/auth/register', data)
```

- [ ] **Step 3: Create `frontend/src/api/schedules.js`**

```js
import api from './axios'
export const listSchedules = () => api.get('/schedules')
export const createSchedule = (data) => api.post('/schedules', data)
```

- [ ] **Step 4: Create `frontend/src/api/reports.js`**

```js
import api from './axios'
export const generateReport = (data) => api.post('/reports/generate', data)
```

- [ ] **Step 5: Create `frontend/src/api/files.js`**

```js
import api from './axios'
export const getDownloadUrl = (documentId) => api.get(`/files/${documentId}/url`)
```

- [ ] **Step 6: Create `frontend/src/api/notifications.js`**

```js
import api from './axios'
export const getUnread = () => api.get('/notifications')
export const markAllRead = () => api.post('/notifications/read-all')
```

- [ ] **Step 7: Create `frontend/src/api/admin.js`**

```js
import api from './axios'
export const listTenants = () => api.get('/tenants')
export const updateRole = (id, role) => api.patch(`/users/${id}/role`, { role })
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/api/
git commit -m "feat: add API layer (axios instance + all domain files)"
```

---

## Task 4: Pinia Stores

**Files:**
- Create: `frontend/src/stores/auth.js`
- Create: `frontend/src/stores/notifications.js`

- [ ] **Step 1: Create `frontend/src/stores/auth.js`**

```js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const username = ref('')

  async function login(credentials) {
    await authApi.login(credentials)
    username.value = credentials.username
  }

  async function logout() {
    await authApi.logout()
    username.value = ''
  }

  return { username, login, logout }
})
```

Note: The backend returns no user info on login (no `/api/auth/me` endpoint), so only `username` is stored. Role enforcement is handled by the backend — the Admin view shows a clear error if the user lacks the ADMIN role.

- [ ] **Step 2: Create `frontend/src/stores/notifications.js`**

```js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUnread, markAllRead as apiMarkAllRead } from '../api/notifications'

export const useNotificationStore = defineStore('notifications', () => {
  const unread = ref([])
  const badgeCount = computed(() => unread.value.length)

  async function fetch() {
    const res = await getUnread()
    unread.value = res.data
  }

  async function markAllRead() {
    await apiMarkAllRead()
    unread.value = []
  }

  return { unread, badgeCount, fetch, markAllRead }
})
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/stores/
git commit -m "feat: add auth and notifications Pinia stores"
```

---

## Task 5: Router

**Files:**
- Create: `frontend/src/router/index.js`

- [ ] **Step 1: Create `frontend/src/router/index.js`**

```js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import DashboardView from '../views/DashboardView.vue'
import SchedulesView from '../views/SchedulesView.vue'
import ReportsView from '../views/ReportsView.vue'
import FilesView from '../views/FilesView.vue'
import AdminView from '../views/AdminView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: LoginView },
  { path: '/register', component: RegisterView },
  { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true } },
  { path: '/schedules', component: SchedulesView, meta: { requiresAuth: true } },
  { path: '/reports', component: ReportsView, meta: { requiresAuth: true } },
  { path: '/files', component: FilesView, meta: { requiresAuth: true } },
  { path: '/admin', component: AdminView, meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.username) {
    return '/login'
  }
})

export default router
```

Note: Views don't exist yet — Vite will error until Task 7–12 create them. Create placeholder stub files to unblock the dev server:

- [ ] **Step 2: Create placeholder stubs for all views**

Create each of these files with identical minimal content:

`frontend/src/views/LoginView.vue`:
```vue
<template><div class="page"><h1>Login</h1></div></template>
```

`frontend/src/views/RegisterView.vue`:
```vue
<template><div class="page"><h1>Register</h1></div></template>
```

`frontend/src/views/DashboardView.vue`:
```vue
<template><div class="page"><h1>Dashboard</h1></div></template>
```

`frontend/src/views/SchedulesView.vue`:
```vue
<template><div class="page"><h1>Schedules</h1></div></template>
```

`frontend/src/views/ReportsView.vue`:
```vue
<template><div class="page"><h1>Reports</h1></div></template>
```

`frontend/src/views/FilesView.vue`:
```vue
<template><div class="page"><h1>Files</h1></div></template>
```

`frontend/src/views/AdminView.vue`:
```vue
<template><div class="page"><h1>Admin</h1></div></template>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/router/ frontend/src/views/
git commit -m "feat: add router with auth guard and view stubs"
```

---

## Task 6: NavBar + NotificationBell

**Files:**
- Create: `frontend/src/components/NavBar.vue`
- Create: `frontend/src/components/NotificationBell.vue`

- [ ] **Step 1: Create `frontend/src/components/NotificationBell.vue`**

```vue
<template>
  <div class="bell-wrapper" @click="toggle">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
      <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
    </svg>
    <span v-if="notifStore.badgeCount > 0" class="badge">{{ notifStore.badgeCount }}</span>
    <div v-if="open" class="dropdown" @click.stop>
      <div class="dropdown-header">
        <span>Notifications</span>
      </div>
      <div v-if="notifStore.unread.length === 0" class="empty">No unread notifications</div>
      <div v-for="n in notifStore.unread" :key="n.id" class="notif-item">
        {{ n.message }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '../stores/notifications'

const notifStore = useNotificationStore()
const open = ref(false)

onMounted(() => notifStore.fetch().catch(() => {}))

function toggle() { open.value = !open.value }
</script>

<style scoped>
.bell-wrapper { position: relative; cursor: pointer; color: var(--text-2); display: flex; align-items: center; }
.badge {
  position: absolute; top: -6px; right: -8px;
  background: #ff3b30; color: white;
  font-size: 10px; font-weight: 700;
  min-width: 16px; height: 16px;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  padding: 0 4px;
}
.dropdown {
  position: absolute; top: 32px; right: -8px;
  background: white; border: 1px solid var(--border);
  border-radius: var(--radius); box-shadow: 0 8px 24px rgba(0,0,0,0.12);
  width: 280px; z-index: 200; overflow: hidden;
}
.dropdown-header {
  padding: 12px 16px;
  font-size: 13px; font-weight: 600;
  border-bottom: 1px solid var(--border);
  color: var(--text-2);
}
.empty { padding: 16px; color: var(--text-2); font-size: 13px; text-align: center; }
.notif-item { padding: 12px 16px; font-size: 13px; border-bottom: 1px solid var(--border); }
.notif-item:last-child { border-bottom: none; }
</style>
```

- [ ] **Step 2: Create `frontend/src/components/NavBar.vue`**

```vue
<template>
  <nav class="navbar">
    <div class="navbar-inner">
      <span class="brand">DocPlatform</span>
      <div class="nav-links">
        <RouterLink to="/dashboard">Dashboard</RouterLink>
        <RouterLink to="/schedules">Schedules</RouterLink>
        <RouterLink to="/reports">Reports</RouterLink>
        <RouterLink to="/files">Files</RouterLink>
        <RouterLink to="/admin">Admin</RouterLink>
      </div>
      <div class="nav-right">
        <NotificationBell />
        <span class="username">{{ authStore.username }}</span>
        <button class="btn btn-ghost btn-sm" @click="handleLogout">Logout</button>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import NotificationBell from './NotificationBell.vue'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.navbar {
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border);
  position: sticky; top: 0; z-index: 100;
}
.navbar-inner {
  max-width: 960px; margin: 0 auto;
  display: flex; align-items: center;
  height: 52px; padding: 0 24px; gap: 32px;
}
.brand { font-weight: 700; font-size: 16px; color: var(--text); }
.nav-links { display: flex; gap: 24px; flex: 1; }
.nav-links a { text-decoration: none; color: var(--text-2); font-size: 14px; font-weight: 500; transition: color 0.15s; }
.nav-links a:hover { color: var(--text); }
.nav-links a.router-link-active { color: var(--accent); }
.nav-right { display: flex; align-items: center; gap: 16px; }
.username { font-size: 13px; color: var(--text-2); }
.btn-sm { padding: 6px 14px; font-size: 13px; }
</style>
```

- [ ] **Step 3: Verify dev server starts**

```bash
cd frontend
npm run dev
```

Open `http://localhost:5173` — should redirect to `/login` (the stub page).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/
git commit -m "feat: add NavBar and NotificationBell components"
```

---

## Task 7: Login + Register Views

**Files:**
- Modify: `frontend/src/views/LoginView.vue` (replace stub)
- Modify: `frontend/src/views/RegisterView.vue` (replace stub)

- [ ] **Step 1: Replace `frontend/src/views/LoginView.vue`**

```vue
<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h1>Sign in</h1>
      <form @submit.prevent="submit">
        <div class="form-group">
          <label>Username</label>
          <input v-model="form.username" type="text" placeholder="your username" required />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="form.password" type="password" placeholder="password" required />
        </div>
        <button class="btn" type="submit" :disabled="loading" style="width:100%">
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
      </form>
      <p class="switch-link">No account? <RouterLink to="/register">Register</RouterLink></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await authStore.login(form)
    router.push('/dashboard')
  } catch (e) {
    error.value = e.response?.data?.message ?? 'Invalid username or password'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; }
.auth-card { width: 100%; max-width: 380px; }
h1 { font-size: 24px; text-align: center; margin-bottom: 28px; }
.btn { margin-top: 8px; }
.switch-link { text-align: center; margin-top: 20px; font-size: 14px; color: var(--text-2); }
.switch-link a { color: var(--accent); text-decoration: none; font-weight: 500; }
</style>
```

- [ ] **Step 2: Replace `frontend/src/views/RegisterView.vue`**

```vue
<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h1>Create account</h1>
      <form @submit.prevent="submit">
        <div class="form-group">
          <label>Tenant Slug</label>
          <input v-model="form.tenantSlug" type="text" placeholder="e.g. acme" required />
        </div>
        <div class="form-group">
          <label>Username</label>
          <input v-model="form.username" type="text" placeholder="your username" required />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="form.password" type="password" placeholder="password" required />
        </div>
        <button class="btn" type="submit" :disabled="loading" style="width:100%">
          {{ loading ? 'Creating account…' : 'Create account' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
        <p class="success-msg" v-if="success">Account created — you can now sign in.</p>
      </form>
      <p class="switch-link">Already have an account? <RouterLink to="/login">Sign in</RouterLink></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { register } from '../api/auth'

const form = reactive({ username: '', password: '', tenantSlug: '' })
const loading = ref(false)
const error = ref('')
const success = ref(false)

async function submit() {
  loading.value = true
  error.value = ''
  success.value = false
  try {
    await register(form)
    success.value = true
    form.username = ''
    form.password = ''
    form.tenantSlug = ''
  } catch (e) {
    error.value = e.response?.data?.message ?? 'Registration failed'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; }
.auth-card { width: 100%; max-width: 380px; }
h1 { font-size: 24px; text-align: center; margin-bottom: 28px; }
.btn { margin-top: 8px; }
.switch-link { text-align: center; margin-top: 20px; font-size: 14px; color: var(--text-2); }
.switch-link a { color: var(--accent); text-decoration: none; font-weight: 500; }
</style>
```

- [ ] **Step 3: Verify in browser**

With backend running, open `http://localhost:5173/login`. Try logging in with wrong credentials — should see error message. Try correct credentials — should redirect to `/dashboard`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/LoginView.vue frontend/src/views/RegisterView.vue
git commit -m "feat: add Login and Register views"
```

---

## Task 8: Dashboard View

**Files:**
- Modify: `frontend/src/views/DashboardView.vue` (replace stub)

- [ ] **Step 1: Replace `frontend/src/views/DashboardView.vue`**

```vue
<template>
  <div class="page">
    <h1>Dashboard</h1>
    <div class="card">
      <div class="card-header">
        <h2>Notifications</h2>
        <button class="btn btn-ghost btn-sm"
                @click="markAll"
                :disabled="!notifStore.unread.length">
          Mark all read
        </button>
      </div>
      <div v-if="loading" class="empty-state">Loading…</div>
      <div v-else-if="notifStore.unread.length === 0" class="empty-state">
        No unread notifications
      </div>
      <div v-for="n in notifStore.unread" :key="n.id" class="notif-row">
        <span class="notif-dot"></span>
        <div>
          <p>{{ n.message }}</p>
          <p class="timestamp">{{ formatDate(n.createdAt) }}</p>
        </div>
      </div>
      <p class="error-msg" v-if="error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '../stores/notifications'

const notifStore = useNotificationStore()
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    await notifStore.fetch()
  } catch {
    error.value = 'Failed to load notifications'
  } finally {
    loading.value = false
  }
})

async function markAll() {
  try {
    await notifStore.markAllRead()
  } catch {
    error.value = 'Failed to mark as read'
  }
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.card-header h2 { margin: 0; }
.notif-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--border); }
.notif-row:last-child { border-bottom: none; }
.notif-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); margin-top: 5px; flex-shrink: 0; }
.timestamp { font-size: 12px; color: var(--text-2); margin-top: 2px; }
.btn-sm { padding: 6px 14px; font-size: 13px; }
</style>
```

- [ ] **Step 2: Verify in browser**

Log in, navigate to `/dashboard`. Notifications load. "Mark all read" clears the list and resets the bell badge.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "feat: add Dashboard view with notifications"
```

---

## Task 9: Schedules View

**Files:**
- Modify: `frontend/src/views/SchedulesView.vue` (replace stub)

- [ ] **Step 1: Replace `frontend/src/views/SchedulesView.vue`**

```vue
<template>
  <div class="page">
    <h1>Schedules</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>Create Schedule</h2>
      <form @submit.prevent="submit">
        <div class="form-row">
          <div class="form-group">
            <label>Name</label>
            <input v-model="form.name" type="text" placeholder="Weekly Sales Report" required />
          </div>
          <div class="form-group">
            <label>Cron Expression</label>
            <input v-model="form.cronExpr" type="text" placeholder="0 8 * * MON" required />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Report Type</label>
            <input v-model="form.reportType" type="text" placeholder="SALES" required />
          </div>
          <div class="form-group">
            <label>Format</label>
            <select v-model="form.format">
              <option value="PDF">PDF</option>
              <option value="EXCEL">Excel</option>
              <option value="CSV">CSV</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>Template ID</label>
          <input v-model="form.templateId" type="text" placeholder="MongoDB template _id" required />
        </div>
        <div class="form-group">
          <label>Recipients (comma-separated emails)</label>
          <input v-model="recipientsRaw" type="text" placeholder="alice@acme.com, bob@acme.com" />
        </div>
        <div class="form-group">
          <label>Params (JSON)</label>
          <textarea v-model="paramsRaw" placeholder='{"region": "US"}'></textarea>
        </div>
        <button class="btn" type="submit" :disabled="creating">
          {{ creating ? 'Creating…' : 'Create Schedule' }}
        </button>
        <p class="error-msg" v-if="createError">{{ createError }}</p>
        <p class="success-msg" v-if="createSuccess">Schedule created.</p>
      </form>
    </div>

    <div class="card">
      <h2>Your Schedules</h2>
      <div v-if="loading" class="empty-state">Loading…</div>
      <div v-else-if="schedules.length === 0 && !listError" class="empty-state">No schedules yet.</div>
      <table v-else-if="schedules.length > 0">
        <thead>
          <tr>
            <th>Name</th><th>Cron</th><th>Format</th><th>Status</th><th>Next Run</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in schedules" :key="s.id">
            <td>{{ s.name }}</td>
            <td><code>{{ s.cronExpr }}</code></td>
            <td>{{ s.format }}</td>
            <td><span :class="['status-badge', s.status.toLowerCase()]">{{ s.status }}</span></td>
            <td>{{ formatDate(s.nextRunAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="listError">{{ listError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listSchedules, createSchedule } from '../api/schedules'

const schedules = ref([])
const loading = ref(false)
const listError = ref('')
const creating = ref(false)
const createError = ref('')
const createSuccess = ref(false)
const recipientsRaw = ref('')
const paramsRaw = ref('')

const form = reactive({
  name: '', cronExpr: '', reportType: '', format: 'PDF', templateId: ''
})

onMounted(loadSchedules)

async function loadSchedules() {
  loading.value = true
  listError.value = ''
  try {
    const res = await listSchedules()
    schedules.value = res.data
  } catch {
    listError.value = 'Failed to load schedules'
  } finally {
    loading.value = false
  }
}

async function submit() {
  creating.value = true
  createError.value = ''
  createSuccess.value = false
  try {
    let params = {}
    if (paramsRaw.value.trim()) params = JSON.parse(paramsRaw.value)
    const recipients = recipientsRaw.value
      ? recipientsRaw.value.split(',').map(s => s.trim()).filter(Boolean)
      : []
    await createSchedule({ ...form, params, recipients })
    createSuccess.value = true
    await loadSchedules()
  } catch (e) {
    createError.value = e.response?.data?.message ?? e.message ?? 'Failed to create schedule'
  } finally {
    creating.value = false
  }
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}
</script>

<style scoped>
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
code { font-size: 12px; background: var(--bg); padding: 2px 6px; border-radius: 4px; }
.status-badge { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
.status-badge.active { background: #d1fae5; color: #065f46; }
.status-badge.paused { background: #fef3c7; color: #92400e; }
</style>
```

- [ ] **Step 2: Verify in browser**

Navigate to `/schedules`. Existing schedules appear in the table. Fill in the create form and submit — new schedule appears in the list.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/SchedulesView.vue
git commit -m "feat: add Schedules view"
```

---

## Task 10: Reports View

**Files:**
- Modify: `frontend/src/views/ReportsView.vue` (replace stub)

- [ ] **Step 1: Replace `frontend/src/views/ReportsView.vue`**

```vue
<template>
  <div class="page">
    <h1>Generate Report</h1>
    <div class="card" style="max-width: 560px;">
      <h2>One-off Report</h2>
      <form @submit.prevent="submit">
        <div class="form-row">
          <div class="form-group">
            <label>Schedule ID</label>
            <input v-model.number="form.scheduleId" type="number" placeholder="42" required />
          </div>
          <div class="form-group">
            <label>Format</label>
            <select v-model="form.format">
              <option value="PDF">PDF</option>
              <option value="EXCEL">Excel</option>
              <option value="CSV">CSV</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>Report Type</label>
          <input v-model="form.reportType" type="text" placeholder="SALES" required />
        </div>
        <div class="form-group">
          <label>Template ID</label>
          <input v-model="form.templateId" type="text" placeholder="MongoDB template _id" required />
        </div>
        <div class="form-group">
          <label>Recipients (comma-separated)</label>
          <input v-model="recipientsRaw" type="text" placeholder="alice@acme.com" />
        </div>
        <div class="form-group">
          <label>Params (JSON)</label>
          <textarea v-model="paramsRaw" placeholder='{"region": "US"}'></textarea>
        </div>
        <button class="btn" type="submit" :disabled="loading">
          {{ loading ? 'Submitting…' : 'Generate Report' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
      </form>

      <div v-if="documentId" class="result-box">
        <p class="result-label">Report queued. Document ID:</p>
        <code class="doc-id">{{ documentId }}</code>
        <p class="result-hint">
          You'll receive a notification when it's ready.
          Copy this ID and use it on the Files page to download.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { generateReport } from '../api/reports'

const form = reactive({ scheduleId: null, reportType: '', format: 'PDF', templateId: '' })
const recipientsRaw = ref('')
const paramsRaw = ref('')
const loading = ref(false)
const error = ref('')
const documentId = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  documentId.value = ''
  try {
    let params = {}
    if (paramsRaw.value.trim()) params = JSON.parse(paramsRaw.value)
    const recipients = recipientsRaw.value
      ? recipientsRaw.value.split(',').map(s => s.trim()).filter(Boolean)
      : []
    const res = await generateReport({ ...form, params, recipients })
    documentId.value = res.data.documentId
  } catch (e) {
    error.value = e.response?.data?.message ?? e.message ?? 'Failed to submit report'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.result-box {
  margin-top: 24px; padding: 16px;
  background: var(--bg); border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}
.result-label { font-size: 13px; color: var(--text-2); margin-bottom: 6px; }
.doc-id {
  display: block; font-size: 13px;
  background: white; padding: 8px 12px;
  border-radius: 6px; border: 1px solid var(--border);
  word-break: break-all; margin-bottom: 8px;
}
.result-hint { font-size: 12px; color: var(--text-2); line-height: 1.6; }
</style>
```

- [ ] **Step 2: Verify in browser**

Navigate to `/reports`. Fill in the form and submit — result box appears with the returned `documentId`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/ReportsView.vue
git commit -m "feat: add Reports view"
```

---

## Task 11: Files View

**Files:**
- Modify: `frontend/src/views/FilesView.vue` (replace stub)

- [ ] **Step 1: Replace `frontend/src/views/FilesView.vue`**

```vue
<template>
  <div class="page">
    <h1>Download File</h1>
    <div class="card" style="max-width: 480px;">
      <h2>Get Download Link</h2>
      <form @submit.prevent="getUrl">
        <div class="form-group">
          <label>Document ID</label>
          <input v-model="documentId" type="text" placeholder="64a3f..." required />
        </div>
        <button class="btn" type="submit" :disabled="loading">
          {{ loading ? 'Fetching…' : 'Get Link' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
      </form>

      <div v-if="url" class="result-box">
        <p class="result-label">Presigned URL (valid 5 min):</p>
        <a :href="url" target="_blank" class="download-link">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          Download file
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getDownloadUrl } from '../api/files'

const documentId = ref('')
const url = ref('')
const loading = ref(false)
const error = ref('')

async function getUrl() {
  loading.value = true
  error.value = ''
  url.value = ''
  try {
    const res = await getDownloadUrl(documentId.value)
    url.value = res.data.url
  } catch (e) {
    error.value = e.response?.status === 404
      ? 'Document not found'
      : 'Access denied or document unavailable'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.result-box {
  margin-top: 24px; padding: 16px;
  background: var(--bg); border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}
.result-label { font-size: 13px; color: var(--text-2); margin-bottom: 8px; }
.download-link {
  display: inline-flex; align-items: center; gap: 6px;
  color: var(--accent); font-weight: 500; font-size: 15px;
  text-decoration: none;
}
.download-link:hover { text-decoration: underline; }
</style>
```

- [ ] **Step 2: Verify in browser**

Navigate to `/files`. Enter a valid `documentId` → presigned download link appears.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/FilesView.vue
git commit -m "feat: add Files view"
```

---

## Task 12: Admin View

**Files:**
- Modify: `frontend/src/views/AdminView.vue` (replace stub)

- [ ] **Step 1: Replace `frontend/src/views/AdminView.vue`**

```vue
<template>
  <div class="page">
    <h1>Admin</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>Tenants</h2>
      <div v-if="tenantsLoading" class="empty-state">Loading…</div>
      <div v-else-if="tenants.length === 0 && !tenantsError" class="empty-state">No tenants found.</div>
      <table v-else-if="tenants.length > 0">
        <thead>
          <tr><th>ID</th><th>Name</th><th>Slug</th><th>Plan</th><th>Created</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in tenants" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ t.name }}</td>
            <td>{{ t.slug }}</td>
            <td>{{ t.plan ?? '—' }}</td>
            <td>{{ formatDate(t.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="tenantsError">{{ tenantsError }}</p>
    </div>

    <div class="card" style="max-width: 400px;">
      <h2>Update User Role</h2>
      <form @submit.prevent="submitRole">
        <div class="form-group">
          <label>User ID</label>
          <input v-model.number="roleForm.userId" type="number" placeholder="2" required />
        </div>
        <div class="form-group">
          <label>Role</label>
          <select v-model="roleForm.role">
            <option value="ADMIN">ADMIN</option>
            <option value="USER">USER</option>
          </select>
        </div>
        <button class="btn" type="submit" :disabled="roleUpdating">
          {{ roleUpdating ? 'Updating…' : 'Update Role' }}
        </button>
        <p class="error-msg" v-if="roleError">{{ roleError }}</p>
        <p class="success-msg" v-if="roleSuccess">Role updated successfully.</p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listTenants, updateRole } from '../api/admin'

const tenants = ref([])
const tenantsLoading = ref(false)
const tenantsError = ref('')

const roleForm = reactive({ userId: null, role: 'USER' })
const roleUpdating = ref(false)
const roleError = ref('')
const roleSuccess = ref(false)

onMounted(loadTenants)

async function loadTenants() {
  tenantsLoading.value = true
  tenantsError.value = ''
  try {
    const res = await listTenants()
    tenants.value = res.data
  } catch (e) {
    tenantsError.value = e.response?.status === 403
      ? 'Access denied — Admin role required'
      : 'Failed to load tenants'
  } finally {
    tenantsLoading.value = false
  }
}

async function submitRole() {
  roleUpdating.value = true
  roleError.value = ''
  roleSuccess.value = false
  try {
    await updateRole(roleForm.userId, roleForm.role)
    roleSuccess.value = true
  } catch (e) {
    roleError.value = e.response?.status === 403
      ? 'Access denied — Admin role required'
      : e.response?.data?.message ?? 'Failed to update role'
  } finally {
    roleUpdating.value = false
  }
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}
</script>
```

- [ ] **Step 2: Verify in browser**

Log in as an ADMIN user, navigate to `/admin`. Tenants table loads. Fill in a user ID + role and submit — success message appears. Log in as a USER, navigate to `/admin` — "Access denied" error shows.

- [ ] **Step 3: Final commit**

```bash
git add frontend/src/views/AdminView.vue
git commit -m "feat: add Admin view — tenant list and role management"
```

---

## Done

Run `npm run dev` inside `frontend/`, open `http://localhost:5173`, and walk through the full flow:

1. Register at `/register` (needs a tenant to exist first — create one via `POST /api/tenants`)
2. Log in at `/login`
3. Check notifications at `/dashboard`
4. Create a schedule at `/schedules`
5. Trigger a report at `/reports`, copy the `documentId`
6. Get the download link at `/files`
7. If ADMIN: manage tenants and roles at `/admin`
