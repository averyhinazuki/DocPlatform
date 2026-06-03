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
            <input v-model.number="form.scheduleId" type="number" placeholder="42 (optional)" />
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
          <label>Data File <span class="hint">(.csv or .xlsx — header row must match variable names)</span></label>
          <input ref="fileInput" type="file" accept=".csv,.xlsx" style="display:none" @change="onFileChange" />
          <div v-if="attachedFile" class="file-chip">
            <span>{{ attachedFile.name }}</span>
            <button type="button" class="chip-clear" @click="clearFile">×</button>
          </div>
          <button v-else type="button" class="btn btn-ghost btn-sm" @click="fileInput.click()">
            Attach Data File
          </button>
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
          <label>Note <span class="hint">(optional — travels with the report)</span></label>
          <textarea v-model="form.note" placeholder="e.g. Use Q1 figures, exclude returns" rows="2"></textarea>
        </div>

        <div v-if="isRteMode" class="form-group">
          <label>Content <span class="hint">(pre-filled from template — edit before generating)</span></label>
          <RteEditor v-model="rteContent" />
        </div>

        <div v-else-if="selectedTemplate?.variables?.length" class="form-group">
          <label>Report Data</label>
          <div class="params-grid">
            <div v-for="v in selectedTemplate.variables" :key="v" class="param-row">
              <label class="param-label">{{ humanize(v) }}</label>
              <input type="text" v-model="paramsForm[v]" :placeholder="v" />
            </div>
          </div>
        </div>
        <button class="btn" type="submit" :disabled="loading">
          {{ loading ? 'Submitting…' : 'Generate Report' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
      </form>

      <div v-if="documentId" class="result-box">
        <p class="result-label">Report queued — you'll be notified when it's ready.</p>
      </div>
    </div>

    <div class="card history-card">
      <div class="history-header">
        <h2>Report History</h2>
        <button class="btn btn-ghost btn-sm" @click="loadHistory" :disabled="historyLoading">
          {{ historyLoading ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>
      <div v-if="historyLoading" class="h-hint">Loading…</div>
      <div v-else-if="history.length === 0" class="h-hint">No reports yet.</div>
      <table v-else class="history-table">
        <thead>
          <tr><th>Format</th><th>Status</th><th>Note</th><th>Generated</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="doc in history" :key="doc.id">
            <td><span class="badge" :class="doc.fileFormat.toLowerCase()">{{ doc.fileFormat }}</span></td>
            <td><span class="status-chip" :class="doc.status.toLowerCase()">{{ doc.status }}</span></td>
            <td class="note-cell">{{ doc.note || '—' }}</td>
            <td class="date-cell">{{ relativeDate(doc.generatedAt) }}</td>
            <td class="action-cell">
              <button
                v-if="doc.status === 'COMPLETED'"
                class="btn btn-ghost btn-sm"
                @click="preview(doc.id)"
              >Preview</button>
              <button
                class="btn-remove"
                title="Delete report"
                @click="removeDoc(doc.id)"
              >✕</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import { generateReport } from '../api/reports'
import { listDocuments, deleteDocument } from '../api/files'
import { listUsers } from '../api/users'
import { listTemplates, getTemplate } from '../api/templates'
import { getMyAssignments } from '../api/assignments'
import { useAuthStore } from '../stores/auth'
import RteEditor from '../components/RteEditor.vue'
import { wrapHtml, extractBody } from '../utils/htmlTemplate.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const assignmentId = computed(() => route.query.assignmentId ? Number(route.query.assignmentId) : null)
const assignmentMode = computed(() => assignmentId.value != null)
const assignmentNotes = ref('')

const form = reactive({ scheduleId: null, reportType: '', format: 'PDF', templateId: '', note: '' })
const selectedRecipients = ref([])
const tenantUsers = ref([])
const templates = ref([])
const selectedTemplateId = ref('')
const selectedTemplate = computed(() => templates.value.find(t => t.id === selectedTemplateId.value) ?? null)
const paramsForm = reactive({})
const fileInput = ref(null)
const attachedFile = ref(null)
const loading = ref(false)
const error = ref('')
const documentId = ref('')
const history = ref([])
const historyLoading = ref(false)
const rteContent = ref('')
const isRteMode = computed(() =>
  selectedTemplate.value !== null &&
  (selectedTemplate.value.variables?.length ?? 0) === 0 &&
  form.format === 'PDF'
)

watch(selectedTemplateId, async id => {
  const t = templates.value.find(t => t.id === id)
  if (!t) return
  form.templateId = t.id
  form.reportType = t.type
  Object.keys(paramsForm).forEach(k => delete paramsForm[k])
  if (t.variables) t.variables.forEach(v => { paramsForm[v] = '' })
  rteContent.value = ''
  if ((t.variables?.length ?? 0) === 0 && form.format === 'PDF') {
    try {
      const res = await getTemplate(id)
      rteContent.value = extractBody(res.data.thymeleafTemplate ?? '')
    } catch { /* non-critical */ }
  }
})

watch(() => form.format, async fmt => {
  rteContent.value = ''
  const t = selectedTemplate.value
  if (!t || (t.variables?.length ?? 0) !== 0) return
  if (fmt === 'PDF') {
    try {
      const res = await getTemplate(t.id)
      rteContent.value = extractBody(res.data.thymeleafTemplate ?? '')
    } catch { /* non-critical */ }
  }
})

function onFileChange(e) { attachedFile.value = e.target.files[0] ?? null }
function clearFile() { attachedFile.value = null; fileInput.value.value = '' }

function humanize(key) {
  return key
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, c => c.toUpperCase())
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await listDocuments()
    history.value = res.data
  } catch { /* non-critical */ }
  finally { historyLoading.value = false }
}

function preview(id) {
  router.push({ path: '/files', query: { docId: id } })
}

async function removeDoc(id) {
  if (!confirm('Delete this report? This cannot be undone.')) return
  try {
    await deleteDocument(id)
    history.value = history.value.filter(d => d.id !== id)
  } catch { /* non-critical */ }
}

function relativeDate(iso) {
  if (!iso) return '—'
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 60) return mins + 'm ago'
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return hrs + 'h ago'
  return Math.floor(hrs / 24) + 'd ago'
}

onMounted(async () => {
  loadHistory()
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
    const params = { ...paramsForm }
    const payload = {
      ...form,
      params,
      recipients: selectedRecipients.value,
      contentOverride: isRteMode.value ? wrapHtml(rteContent.value) : null,
      ...(assignmentMode.value ? { assignmentId: assignmentId.value, scheduleId: null } : {})
    }
    const res = await generateReport(payload, attachedFile.value)
    documentId.value = res.data.documentId
    clearFile()
    loadHistory()
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
.file-chip {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 12px; border-radius: 20px;
  background: var(--bg); border: 1px solid var(--border); font-size: 13px;
}
.chip-clear {
  background: none; border: none; cursor: pointer;
  font-size: 16px; color: var(--text-2); line-height: 1; padding: 0;
}
.params-grid { display: flex; flex-direction: column; gap: 10px; }
.param-row { display: grid; grid-template-columns: 140px 1fr; align-items: center; gap: 12px; }
.param-label { font-size: 13px; color: var(--text-2); font-weight: 500; }

.history-card { margin-top: 24px; }
.history-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.history-header h2 { margin: 0; }
.h-hint { padding: 12px 0; color: var(--text-2); font-size: 13px; }
.history-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.history-table th { text-align: left; font-weight: 600; padding: 6px 10px; border-bottom: 2px solid var(--border); }
.history-table td { padding: 8px 10px; border-bottom: 1px solid var(--border); vertical-align: middle; }
.note-cell { color: var(--text-2); max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.date-cell { color: var(--text-2); white-space: nowrap; }

.badge { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
.badge.pdf   { background: #fee2e2; color: #b91c1c; }
.badge.excel { background: #dcfce7; color: #15803d; }
.badge.csv   { background: #e0f2fe; color: #0369a1; }

.status-chip { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
.status-chip.pending     { background: #fef9c3; color: #854d0e; }
.status-chip.in_progress { background: #dbeafe; color: #1d4ed8; }
.status-chip.completed   { background: #dcfce7; color: #15803d; }
.status-chip.failed      { background: #fee2e2; color: #b91c1c; }

.action-cell { display: flex; align-items: center; gap: 6px; }
.btn-remove {
  background: none; border: none; cursor: pointer;
  color: var(--text-2); font-size: 13px; padding: 2px 6px; border-radius: 4px; line-height: 1;
}
.btn-remove:hover { background: #fee2e2; color: #dc2626; }
</style>
