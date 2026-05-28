<template>
  <div class="page">
    <h1>Generate Report</h1>
    <div class="card" style="max-width: 560px;">
      <h2>One-off Report</h2>
      <form @submit.prevent="submit">
        <div class="form-row">
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
        <div class="form-group">
          <label>Report Type</label>
          <input v-model="form.reportType" type="text" placeholder="SALES" required />
        </div>
        <div class="form-group">
          <label>Template ID</label>
          <input v-model="form.templateId" type="text" placeholder="MongoDB template _id" required />
        </div>
        <div class="form-group">
          <label>Recipients (comma-separated)</label>
          <input v-model="recipientsRaw" type="text" placeholder="alice@acme.com" />
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
import { ref, reactive } from 'vue'
import { generateReport } from '../api/reports'

const form = reactive({ scheduleId: null, reportType: '', format: 'PDF', templateId: '' })
const recipientsRaw = ref('')
const paramsRaw = ref('')
const loading = ref(false)
const error = ref('')
const documentId = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  documentId.value = ''
  try {
    let params = {}
    if (paramsRaw.value.trim()) params = JSON.parse(paramsRaw.value)
    const recipients = recipientsRaw.value
      ? recipientsRaw.value.split(',').map(s => s.trim()).filter(Boolean)
      : []
    const res = await generateReport({ ...form, params, recipients })
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
