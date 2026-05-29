<template>
  <div class="page">
    <h1>Assignments</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>Create Schedule</h2>
      <form @submit.prevent="submitSchedule">
        <div class="form-group">
          <label>Name</label>
          <input v-model="scheduleForm.name" type="text" placeholder="Weekly Sales Report" required />
        </div>
        <div class="form-group">
          <label>Schedule</label>
          <div class="cron-builder">
            <select v-model="cronMode">
              <option value="hourly">Every hour</option>
              <option value="daily">Daily</option>
              <option value="weekly">Weekly</option>
              <option value="monthly">Monthly</option>
              <option value="custom">Custom (cron expression)</option>
            </select>
            <template v-if="cronMode !== 'hourly' && cronMode !== 'custom'">
              <span class="cron-label">at</span>
              <select v-model="cronHour">
                <option v-for="h in 24" :key="h - 1" :value="String(h - 1)">{{ formatHour(h - 1) }}</option>
              </select>
            </template>
            <template v-if="cronMode === 'weekly'">
              <span class="cron-label">on</span>
              <select v-model="cronWeekday">
                <option value="MON">Monday</option>
                <option value="TUE">Tuesday</option>
                <option value="WED">Wednesday</option>
                <option value="THU">Thursday</option>
                <option value="FRI">Friday</option>
                <option value="SAT">Saturday</option>
                <option value="SUN">Sunday</option>
              </select>
            </template>
            <template v-if="cronMode === 'monthly'">
              <span class="cron-label">on day</span>
              <select v-model="cronMonthDay">
                <option v-for="d in 28" :key="d" :value="String(d)">{{ d }}</option>
              </select>
            </template>
            <input v-if="cronMode === 'custom'" v-model="scheduleForm.cronExpr"
                   type="text" placeholder="0 0 8 * * MON" class="cron-raw" required />
            <code v-if="cronMode !== 'custom'" class="cron-preview">{{ cronExprComputed }}</code>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Template</label>
            <select v-model="selectedTemplateId" required>
              <option value="" disabled>Select a template…</option>
              <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} ({{ t.type }})</option>
            </select>
            <span v-if="selectedTemplate" class="hint" style="margin-top:4px;display:block">
              Params: {{ selectedTemplate.variables?.join(', ') || 'none' }}
            </span>
          </div>
          <div class="form-group">
            <label>Format</label>
            <select v-model="scheduleForm.format">
              <option value="PDF">PDF</option>
              <option value="EXCEL">Excel</option>
              <option value="CSV">CSV</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>Recipients</label>
          <div class="user-picker">
            <label v-for="u in tenantUsers" :key="u.id" class="user-pick-item">
              <input type="checkbox" :value="u.username" v-model="selectedRecipients" />
              {{ u.username }}
            </label>
            <span v-if="tenantUsers.length === 0" class="hint">No users in this tenant.</span>
          </div>
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

    <div class="card" style="margin-bottom: 24px;">
      <h2>Your Schedules</h2>
      <div v-if="schedulesLoading" class="empty-state">Loading…</div>
      <div v-else-if="schedules.length === 0 && !scheduleListError" class="empty-state">No schedules yet.</div>
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
            <td><span :class="['status-badge', s.status?.toLowerCase()]">{{ s.status }}</span></td>
            <td>{{ formatDate(s.nextRunAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="scheduleListError">{{ scheduleListError }}</p>
    </div>

    <div v-if="authStore.role === 'ADMIN'" class="card" style="margin-bottom: 24px;">
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

    <div v-if="authStore.role === 'ADMIN'" class="card">
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listSchedules, createSchedule } from '../api/schedules'
import { listUsers } from '../api/users'
import { listTemplates } from '../api/templates'
import { createAssignment, listAssignments } from '../api/assignments'

const authStore = useAuthStore()

// shared
const tenantUsers = ref([])
const templates = ref([])

// schedule state
const schedules = ref([])
const schedulesLoading = ref(false)
const scheduleListError = ref('')
const creating = ref(false)
const createError = ref('')
const createSuccess = ref(false)
const selectedRecipients = ref([])
const paramsRaw = ref('')
const selectedTemplateId = ref('')
const selectedTemplate = computed(() => templates.value.find(t => t.id === selectedTemplateId.value) ?? null)
const scheduleForm = reactive({ name: '', cronExpr: '', reportType: '', format: 'PDF', templateId: '' })

const cronMode = ref('daily')
const cronHour = ref('8')
const cronWeekday = ref('MON')
const cronMonthDay = ref('1')

const cronExprComputed = computed(() => {
  switch (cronMode.value) {
    case 'hourly':  return '0 0 * * * *'
    case 'daily':   return `0 0 ${cronHour.value} * * *`
    case 'weekly':  return `0 0 ${cronHour.value} * * ${cronWeekday.value}`
    case 'monthly': return `0 0 ${cronHour.value} ${cronMonthDay.value} * *`
    default:        return scheduleForm.cronExpr
  }
})

watch(selectedTemplateId, id => {
  const t = templates.value.find(t => t.id === id)
  if (t) { scheduleForm.templateId = t.id; scheduleForm.reportType = t.type }
})

// assignment state (admin only)
const assignForm = reactive({ assigneeId: '', templateId: '', notes: '' })
const assigning = ref(false)
const assignError = ref('')
const assignSuccess = ref(false)
const assignments = ref([])
const assignmentsLoading = ref(false)
const assignmentsError = ref('')

onMounted(async () => {
  await Promise.all([loadSchedules(), loadUsersAndTemplates()])
  if (authStore.role === 'ADMIN') await loadAssignments()
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

async function loadSchedules() {
  schedulesLoading.value = true
  scheduleListError.value = ''
  try {
    const res = await listSchedules()
    schedules.value = res.data
  } catch {
    scheduleListError.value = 'Failed to load schedules'
  } finally {
    schedulesLoading.value = false
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

async function submitSchedule() {
  creating.value = true
  createError.value = ''
  createSuccess.value = false
  try {
    let params = {}
    if (paramsRaw.value.trim()) {
      try {
        params = JSON.parse(paramsRaw.value)
      } catch {
        createError.value = 'Invalid JSON in Params field'
        creating.value = false
        return
      }
    }
    await createSchedule({ ...scheduleForm, cronExpr: cronExprComputed.value, params, recipients: selectedRecipients.value })
    createSuccess.value = true
    createSuccess.value = false
    Object.assign(scheduleForm, { name: '', cronExpr: '', reportType: '', format: 'PDF', templateId: '' })
    selectedTemplateId.value = ''
    selectedRecipients.value = []
    paramsRaw.value = ''
    cronMode.value = 'daily'
    cronHour.value = '8'
    cronWeekday.value = 'MON'
    cronMonthDay.value = '1'
    await loadSchedules()
  } catch (e) {
    createError.value = e.response?.data?.message ?? e.message ?? 'Failed to create schedule'
  } finally {
    creating.value = false
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

function formatHour(h) {
  if (h === 0) return '12 AM'
  if (h < 12) return `${h} AM`
  if (h === 12) return '12 PM'
  return `${h - 12} PM`
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
.badge-pending { background: #fef3c7; color: #92400e; }
.badge-done { background: #d1fae5; color: #065f46; }
.user-picker { display: flex; flex-wrap: wrap; gap: 8px; padding: 10px; background: var(--bg); border: 1px solid var(--border); border-radius: var(--radius-sm); }
.user-pick-item { display: flex; align-items: center; gap: 6px; font-size: 13px; cursor: pointer; }
.hint { font-size: 12px; color: var(--text-2); }
.notes-cell { max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cron-builder { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.cron-builder select { padding: 7px 10px; border: 1px solid var(--border); border-radius: var(--radius-sm); font-size: 13px; background: white; }
.cron-label { font-size: 13px; color: var(--text-2); }
.cron-raw { flex: 1; padding: 7px 10px; border: 1px solid var(--border); border-radius: var(--radius-sm); font-size: 13px; font-family: monospace; }
.cron-preview { font-size: 12px; color: var(--text-2); background: var(--bg); padding: 4px 8px; border-radius: 4px; border: 1px solid var(--border); }
</style>
