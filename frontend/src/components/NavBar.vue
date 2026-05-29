<template>
  <nav class="navbar">
    <div class="navbar-inner">
      <span class="brand">DocPlatform</span>
      <div class="nav-links">
        <RouterLink to="/dashboard">Dashboard</RouterLink>
        <RouterLink to="/reports">Reports</RouterLink>
        <RouterLink to="/files">Files</RouterLink>
        <RouterLink to="/templates">Templates</RouterLink>
        <RouterLink to="/assignments">Assignments</RouterLink>
        <RouterLink v-if="authStore.role === 'ADMIN'" to="/admin">Admin</RouterLink>
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
