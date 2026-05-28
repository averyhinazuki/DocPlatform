<template>
  <div class="page">
    <h1>Generate Report</h1>
    <div class="card" style="max-width: 560px;">
      <h2>{{ assignmentMode ? 'Complete Assignment' : 'One-off Report' }}</h2>

      <div v-if="assignmentMode && assignmentNotes" class="note-banner">
        <strong>Admin note:</strong> {{ assignmentNotes }}
      </div>

      <form @submit.prevent="submit">
        <div class="form-row" v-if="!assignmentMode">
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
        <div class="form-group" v-if="assignmentMode">
          <label>Format</label>
          <select v-model="form.format">
            <option value="PDF">PDF</option>
            <option value="EXCEL">Excel</option>
            <option value="CSV">CSV</option>
          </select>
        </div>
        <div class="form-group">
          <label>Template</label>
          <select v-model="selectedTemplateId" :disabled="assignmentMode" required>
            <option value="" disabled>Select a template…</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} ({{ t.type }})</option>
          </select>
          <span v-if="selectedTemplate" class="hint" style="margin-top:4px;display:block">
            Params: {{ selectedTemplate.variables?.join(', ') || 'none' }}
          </span>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { generateReport } from '../api/reports'
import { listUsers } from '../api/users'
import { listTemplates } from '../api/templates'
import { getMyAssignments } from '../api/assignments'

const route = useRoute()
const assignmentId = computed(() => route.query.assignmentId ? Number(route.query.assignmentId) : null)
const assignmentMode = computed(() => assignmentId.value != null)
const assignmentNotes = ref('')

const form = reactive({ scheduleId: null, reportType: '', format: 'PDF', templateId: '' })
const selectedRecipients = ref([])
const tenantUsers = ref([])
const templates = ref([])
const selectedTemplateId = ref('')
const selectedTemplate = computed(() => templates.value.find(t => t.id === selectedTemplateId.value) ?? null)
const paramsRaw = ref('')
const loading = ref(false)
const error = ref('')
const documentId = ref('')

watch(selectedTemplateId, id => {
  const t = templates.value.find(t => t.id === id)
  if (t) { form.templateId = t.id; form.reportType = t.type }
})

onMounted(async () => {
  try {
    const [usersRes, templatesRes] = await Promise.all([listUsers(), listTemplates()])
    tenantUsers.value = usersRes.data
    templates.value = templatesRes.data

    if (assignmentMode.value) {
      const queryTemplateId = route.query.templateId
      if (queryTemplateId) selectedTemplateId.value = queryTemplateId

      const mineRes = await getMyAssignments()
      const match = mineRes.data.find(a => a.id === assignmentId.value)
      if (match) assignmentNotes.value = match.notes ?? ''
    }
  } catch {
    // non-critical
  }
})

async function submit() {
  loading.value = true
  error.value = ''
  documentId.value = ''
  try {
    let params = {}
    if (paramsRaw.value.trim()) {
      try {
        params = JSON.parse(paramsRaw.value)
      } catch {
        error.value = 'Invalid JSON in Params field'
        loading.value = false
        return
      }
    }
    const payload = {
      ...form,
      params,
      recipients: selectedRecipients.value,
      ...(assignmentMode.value ? { assignmentId: assignmentId.value, scheduleId: null } : {})
    }
    const res = await generateReport(payload)
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
.note-banner {
  padding: 12px 14px; margin-bottom: 20px;
  background: #eff6ff; border: 1px solid #bfdbfe; border-radius: var(--radius-sm);
  font-size: 14px; color: #1e40af;
}
.user-picker { display: flex; flex-wrap: wrap; gap: 8px; padding: 10px; background: var(--bg); border: 1px solid var(--border); border-radius: var(--radius-sm); }
.user-pick-item { display: flex; align-items: center; gap: 6px; font-size: 13px; cursor: pointer; }
.hint { font-size: 13px; color: var(--text-2); }
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
