<template>
  <div class="page">
    <h1>Download File</h1>
    <div class="card" style="max-width: 480px;">
      <h2>Get Download Link</h2>
      <form @submit.prevent="getUrl">
        <div class="form-group">
          <label>Document ID</label>
          <input v-model="documentId" type="text" placeholder="64a3f..." required />
        </div>
        <button class="btn" type="submit" :disabled="loading">
          {{ loading ? 'Fetching…' : 'Get Link' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
      </form>

      <div v-if="url" class="result-box">
        <p class="result-label">Presigned URL (valid 5 min):</p>
        <a :href="url" target="_blank" class="download-link">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          Download file
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getDownloadUrl } from '../api/files'

const documentId = ref('')
const url = ref('')
const loading = ref(false)
const error = ref('')

async function getUrl() {
  loading.value = true
  error.value = ''
  url.value = ''
  try {
    const res = await getDownloadUrl(documentId.value)
    url.value = res.data.url
  } catch (e) {
    error.value = e.response?.status === 404
      ? 'Document not found'
      : 'Access denied or document unavailable'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.result-box {
  margin-top: 24px; padding: 16px;
  background: var(--bg); border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}
.result-label { font-size: 13px; color: var(--text-2); margin-bottom: 8px; }
.download-link {
  display: inline-flex; align-items: center; gap: 6px;
  color: var(--accent); font-weight: 500; font-size: 15px;
  text-decoration: none;
}
.download-link:hover { text-decoration: underline; }
</style>
