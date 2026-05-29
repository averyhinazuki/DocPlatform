<template>
  <div class="page">
    <h1>Templates</h1>

    <div v-if="authStore.role === 'ADMIN'" class="card" style="margin-bottom: 24px;">
      <h2>Create Template</h2>
      <form @submit.prevent="submit">
        <div class="form-row">
          <div class="form-group">
            <label>Name</label>
            <input v-model="form.name" type="text" placeholder="Monthly Sales Report" required />
          </div>
          <div class="form-group">
            <label>Report Type</label>
            <input v-model="form.type" type="text" placeholder="SALES" required />
          </div>
        </div>
        <div class="form-group">
          <label>Variables <span class="hint">(comma-separated — these become the params keys)</span></label>
          <input v-model="variablesRaw" type="text" placeholder="region, sales, period" />
        </div>
        <div v-if="!variablesRaw.trim()" class="form-group">
          <label>Content <span class="hint">(PDF body — compose the document here)</span></label>
          <RteEditor v-model="rteContent" />
        </div>
        <button class="btn" type="submit" :disabled="creating">
          {{ creating ? 'Creating…' : 'Create Template' }}
        </button>
        <p class="error-msg" v-if="createError">{{ createError }}</p>
        <p class="success-msg" v-if="createSuccess">Template created.</p>
      </form>
    </div>

    <div class="card">
      <h2>Available Templates</h2>
      <div v-if="loading" class="empty-state">Loading…</div>
      <div v-else-if="templates.length === 0 && !listError" class="empty-state">No templates yet.</div>
      <table v-else-if="templates.length > 0">
        <thead>
          <tr>
            <th>Name</th><th>Type</th><th>Variables</th><th>ID</th>
            <th v-if="authStore.role === 'ADMIN'"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in templates" :key="t.id">
            <td>{{ t.name }}</td>
            <td><span class="type-badge">{{ t.type }}</span></td>
            <td>
              <span v-if="t.variables?.length" class="vars">{{ t.variables.join(', ') }}</span>
              <span v-else class="hint">—</span>
            </td>
            <td><code class="id-cell">{{ t.id }}</code></td>
            <td v-if="authStore.role === 'ADMIN'">
              <button class="btn-remove" @click="remove(t.id)" title="Delete template">✕</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="listError">{{ listError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listTemplates, createTemplate, deleteTemplate } from '../api/templates'
import { useAuthStore } from '../stores/auth'
import RteEditor from '../components/RteEditor.vue'
import { wrapHtml } from '../utils/htmlTemplate.js'

const authStore = useAuthStore()
const templates = ref([])
const loading = ref(false)
const listError = ref('')
const creating = ref(false)
const createError = ref('')
const createSuccess = ref(false)
const variablesRaw = ref('')
const rteContent = ref('')
const form = reactive({ name: '', type: '', thymeleafTemplate: '' })

function humanize(key) {
  return key
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, c => c.toUpperCase())
}

function buildPdfTemplate(name, variables) {
  const headerCells = variables.map(v => `        <th>${humanize(v)}</th>`).join('\n')
  const dataCells   = variables.map(v => `        <td th:text="\${row['${v}']}">—</td>`).join('\n')
  return `<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8"/>
  <style>
    body { font-family: Arial, sans-serif; margin: 32px; color: #222; }
    h2 { margin-bottom: 16px; }
    table { width: 100%; border-collapse: collapse; }
    th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
    th { background: #f5f5f5; font-weight: 600; }
  </style>
</head>
<body>
  <h2>${name}</h2>
  <table>
    <thead>
      <tr>
${headerCells}
      </tr>
    </thead>
    <tbody>
      <tr th:each="row : \${rows}">
${dataCells}
      </tr>
    </tbody>
  </table>
</body>
</html>`
}

onMounted(load)

async function load() {
  loading.value = true
  listError.value = ''
  try {
    const res = await listTemplates()
    templates.value = res.data
  } catch {
    listError.value = 'Failed to load templates'
  } finally {
    loading.value = false
  }
}

async function remove(id) {
  if (!confirm('Delete this template? This cannot be undone.')) return
  try {
    await deleteTemplate(id)
    await load()
  } catch {
    // non-critical: list will be stale until next refresh
  }
}

async function submit() {
  creating.value = true
  createError.value = ''
  createSuccess.value = false
  try {
    const variables = variablesRaw.value
      ? variablesRaw.value.split(',').map(s => s.trim()).filter(Boolean)
      : []
    const thymeleafTemplate = variables.length === 0
      ? wrapHtml(rteContent.value)
      : buildPdfTemplate(form.name, variables)
    await createTemplate({ ...form, thymeleafTemplate, variables })
    createSuccess.value = true
    variablesRaw.value = ''
    rteContent.value = ''
    Object.assign(form, { name: '', type: '', thymeleafTemplate: '' })
    await load()
  } catch (e) {
    createError.value = e.response?.data?.message ?? e.message ?? 'Failed to create template'
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.type-badge { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 12px; background: #e0e7ff; color: #3730a3; }
.vars { font-size: 13px; color: var(--text-2); }
.hint { font-size: 12px; color: var(--text-2); }
.id-cell { font-size: 11px; color: var(--text-2); word-break: break-all; }
code { background: var(--bg); padding: 2px 6px; border-radius: 4px; }
.btn-remove {
  background: none; border: none; cursor: pointer;
  color: var(--text-2); font-size: 13px; padding: 2px 6px; border-radius: 4px;
  line-height: 1;
}
.btn-remove:hover { background: #fee2e2; color: #dc2626; }
</style>
