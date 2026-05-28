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
