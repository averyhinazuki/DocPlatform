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
