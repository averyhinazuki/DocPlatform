<template>
  <div class="page">
    <h1>Dashboard</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>My Assignments</h2>
      <div v-if="assignmentsLoading" class="empty-state">Loading…</div>
      <div v-else-if="assignments.length === 0" class="empty-state">No pending assignments.</div>
      <div v-for="a in assignments" :key="a.id" class="assignment-row">
        <div class="assignment-info">
          <p class="assignment-title">{{ a.templateName }}</p>
          <p v-if="a.notes" class="assignment-note">{{ a.notes }}</p>
          <p class="timestamp">Assigned {{ formatDate(a.createdAt) }}</p>
        </div>
        <router-link
          :to="`/reports?assignmentId=${a.id}&templateId=${a.templateId}`"
          class="btn btn-sm">
          Generate Report
        </router-link>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <h2>Notifications</h2>
        <button class="btn btn-ghost btn-sm"
                @click="markAll"
                :disabled="!notifStore.unread.length">
          Mark all read
        </button>
      </div>
      <div v-if="notifLoading" class="empty-state">Loading…</div>
      <div v-else-if="notifStore.unread.length === 0" class="empty-state">
        No unread notifications
      </div>
      <div v-for="n in notifStore.unread" :key="n.id" class="notif-row">
        <span class="notif-dot"></span>
        <div class="notif-body">
          <p>{{ n.message }}</p>
          <p class="timestamp">{{ formatDate(n.createdAt) }}</p>
        </div>
        <button v-if="n.documentId" class="btn btn-sm preview-btn" @click="navigateToDoc(n.documentId)">
          Preview
        </button>
      </div>
      <p class="error-msg" v-if="notifError">{{ notifError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '../stores/notifications'
import { getMyAssignments } from '../api/assignments'

const notifStore = useNotificationStore()
const router = useRouter()

function navigateToDoc(documentId) {
  router.push('/files?docId=' + documentId)
}
const notifLoading = ref(false)
const notifError = ref('')

const assignments = ref([])
const assignmentsLoading = ref(false)

onMounted(async () => {
  assignmentsLoading.value = true
  notifLoading.value = true
  try {
    const [assignRes] = await Promise.all([
      getMyAssignments(),
      notifStore.fetch()
    ])
    assignments.value = assignRes.data
  } catch {
    notifError.value = 'Failed to load data'
  } finally {
    assignmentsLoading.value = false
    notifLoading.value = false
  }
})

async function markAll() {
  try {
    await notifStore.markAllRead()
  } catch {
    notifError.value = 'Failed to mark as read'
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
.assignment-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 0; border-bottom: 1px solid var(--border);
}
.assignment-row:last-child { border-bottom: none; }
.assignment-info { flex: 1; margin-right: 16px; }
.assignment-title { font-weight: 600; margin-bottom: 2px; }
.assignment-note { font-size: 13px; color: var(--text-2); margin-bottom: 2px; }
.notif-row { display: flex; align-items: center; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--border); }
.notif-row:last-child { border-bottom: none; }
.notif-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); flex-shrink: 0; }
.notif-body { flex: 1; }
.preview-btn { flex-shrink: 0; }
.timestamp { font-size: 12px; color: var(--text-2); margin-top: 2px; }
.btn-sm { padding: 6px 14px; font-size: 13px; white-space: nowrap; }
</style>
