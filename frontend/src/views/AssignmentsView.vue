<template>
  <div class="page">
    <h1>Assignments</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>Assign Report Task</h2>
      <form @submit.prevent="submitAssignment" style="max-width: 480px;">
        <div class="form-group">
          <label>Assignee</label>
          <select v-model.number="assignForm.assigneeId" required>
            <option value="" disabled>Select user…</option>
            <option v-for="u in tenantUsers" :key="u.id" :value="u.id">{{ u.username }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Template</label>
          <select v-model="assignForm.templateId" required>
            <option value="" disabled>Select template…</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} ({{ t.type }})</option>
          </select>
        </div>
        <div class="form-group">
          <label>Notes <span class="hint">(guidance for the user)</span></label>
          <textarea v-model="assignForm.notes" placeholder="Use Q1 2026 figures…" rows="3"></textarea>
        </div>
        <button class="btn" type="submit" :disabled="assigning">
          {{ assigning ? 'Assigning…' : 'Assign Task' }}
        </button>
        <p class="error-msg" v-if="assignError">{{ assignError }}</p>
        <p class="success-msg" v-if="assignSuccess">Assignment created.</p>
      </form>
    </div>

    <div class="card">
      <h2>All Assignments</h2>
      <div v-if="assignmentsLoading" class="empty-state">Loading…</div>
      <div v-else-if="assignments.length === 0 && !assignmentsError" class="empty-state">No assignments yet.</div>
      <table v-else-if="assignments.length > 0">
        <thead>
          <tr>
            <th>Assignee</th><th>Template</th><th>Notes</th>
            <th>Status</th><th>Created</th><th>Completed</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in assignments" :key="a.id">
            <td>{{ a.assigneeUsername }}</td>
            <td>{{ a.templateName }}</td>
            <td class="notes-cell">{{ a.notes || '—' }}</td>
            <td>
              <span :class="['status-badge', a.status === 'COMPLETED' ? 'badge-done' : 'badge-pending']">
                {{ a.status }}
              </span>
            </td>
            <td>{{ formatDate(a.createdAt) }}</td>
            <td>{{ a.completedAt ? formatDate(a.completedAt) : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="assignmentsError">{{ assignmentsError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listUsers } from '../api/users'
import { listTemplates } from '../api/templates'
import { createAssignment, listAssignments } from '../api/assignments'

const tenantUsers = ref([])
const templates = ref([])
const assignForm = reactive({ assigneeId: '', templateId: '', notes: '' })
const assigning = ref(false)
const assignError = ref('')
const assignSuccess = ref(false)

const assignments = ref([])
const assignmentsLoading = ref(false)
const assignmentsError = ref('')

onMounted(async () => {
  await Promise.all([loadUsersAndTemplates(), loadAssignments()])
})

async function loadUsersAndTemplates() {
  try {
    const [usersRes, templatesRes] = await Promise.all([listUsers(), listTemplates()])
    tenantUsers.value = usersRes.data
    templates.value = templatesRes.data
  } catch {
    // non-critical for pickers
  }
}

async function loadAssignments() {
  assignmentsLoading.value = true
  assignmentsError.value = ''
  try {
    const res = await listAssignments()
    assignments.value = res.data
  } catch {
    assignmentsError.value = 'Failed to load assignments'
  } finally {
    assignmentsLoading.value = false
  }
}

async function submitAssignment() {
  assigning.value = true
  assignError.value = ''
  assignSuccess.value = false
  try {
    await createAssignment({
      assigneeId: assignForm.assigneeId,
      templateId: assignForm.templateId,
      notes: assignForm.notes || null
    })
    assignSuccess.value = true
    Object.assign(assignForm, { assigneeId: '', templateId: '', notes: '' })
    await loadAssignments()
  } catch (e) {
    assignError.value = e.response?.data?.message ?? 'Failed to create assignment'
  } finally {
    assigning.value = false
  }
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}
</script>

<style scoped>
.notes-cell { max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.status-badge { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
.badge-pending { background: #fef3c7; color: #92400e; }
.badge-done { background: #d1fae5; color: #065f46; }
.hint { font-size: 12px; color: var(--text-2); }
</style>
