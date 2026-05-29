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
        <div class="notif-content">
          <span class="notif-msg">{{ n.message }}</span>
          <span v-if="n.note" class="notif-note">{{ n.note }}</span>
        </div>
        <button v-if="n.documentId" class="preview-btn" @click.stop="navigate(n.documentId)">
          Preview
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '../stores/notifications'

const notifStore = useNotificationStore()
const router = useRouter()
const open = ref(false)
let pollInterval = null

onMounted(() => {
  notifStore.fetch().catch(() => {})
  pollInterval = setInterval(() => notifStore.fetch().catch(() => {}), 15000)
})

onUnmounted(() => clearInterval(pollInterval))

function toggle() { open.value = !open.value }

function navigate(documentId) {
  open.value = false
  router.push('/files?docId=' + documentId)
}
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
.notif-item {
  padding: 10px 16px;
  font-size: 13px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 8px;
}
.notif-item:last-child { border-bottom: none; }
.notif-content { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.notif-msg { }
.notif-note { font-size: 12px; color: var(--text-2); font-style: italic; }
.preview-btn {
  flex-shrink: 0;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--accent);
  background: transparent;
  border: 1px solid var(--accent);
  border-radius: 4px;
  cursor: pointer;
}
.preview-btn:hover { background: var(--accent); color: white; }
</style>
