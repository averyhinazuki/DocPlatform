<template>
  <div class="page files-page">
    <h1>Files</h1>
    <div class="split">

      <!-- Left panel: document list -->
      <div class="doc-list">
        <div v-if="loadingList" class="list-hint">Loading…</div>
        <div v-else-if="documents.length === 0" class="list-hint">No reports yet.</div>
        <div
          v-for="doc in documents"
          :key="doc.id"
          class="doc-row"
          :class="{ selected: selectedId === doc.id }"
          @click="select(doc)"
        >
          <div class="doc-meta">
            <span class="badge" :class="doc.fileFormat.toLowerCase()">{{ doc.fileFormat }}</span>
            <span class="doc-status">{{ doc.status }}</span>
            <span class="doc-date">{{ relativeDate(doc.generatedAt) }}</span>
          </div>
          <button
            v-if="authStore.role === 'ADMIN'"
            class="btn-remove"
            title="Delete report"
            @click.stop="remove(doc.id)"
          >✕</button>
        </div>
        <div v-if="totalPages > 1" class="pager">
          <button class="pager-btn" :disabled="page === 0" @click="loadPage(page - 1)">‹ Prev</button>
          <span class="pager-info">{{ page + 1 }} / {{ totalPages }}</span>
          <button class="pager-btn" :disabled="page >= totalPages - 1" @click="loadPage(page + 1)">Next ›</button>
        </div>
      </div>

      <!-- Right panel: preview -->
      <div class="preview-panel">
        <div v-if="!selectedDoc" class="empty-hint">Select a report to preview.</div>

        <template v-else>
          <div class="preview-toolbar">
            <a v-if="presignedUrl" :href="presignedUrl" target="_blank" class="btn">
              Download
            </a>
            <p v-if="selectedDoc.note" class="doc-note">{{ selectedDoc.note }}</p>
          </div>

          <div class="preview-body">
            <div v-if="loadingPreview" class="list-hint">Loading preview…</div>
            <div v-else-if="previewError" class="error-msg">{{ previewError }}</div>
            <template v-else-if="presignedUrl">
              <iframe
                v-if="selectedDoc.fileFormat === 'PDF'"
                :src="presignedUrl"
                class="pdf-frame"
              />
              <div v-else class="no-preview">
                Preview not available for {{ selectedDoc.fileFormat }} — use the download button above.
              </div>
            </template>
          </div>
        </template>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listDocuments, getDownloadUrl, deleteDocument } from '../api/files'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const documents = ref([])
const loadingList = ref(true)
const selectedDoc = ref(null)
const selectedId = ref(null)
const presignedUrl = ref('')
const loadingPreview = ref(false)
const previewError = ref('')
const page = ref(0)
const totalPages = ref(0)

async function loadPage(p) {
  loadingList.value = true
  try {
    const res = await listDocuments(p, 20)
    documents.value = res.data.items
    page.value = res.data.page
    totalPages.value = res.data.totalPages
  } finally {
    loadingList.value = false
  }
}

onMounted(async () => {
  await loadPage(0)

  const docId = route.query.docId
  if (docId) {
    const found = documents.value.find(d => d.id === docId)
    if (found) select(found)
  }
})

async function select(doc) {
  selectedDoc.value = doc
  selectedId.value = doc.id
  presignedUrl.value = ''
  previewError.value = ''
  loadingPreview.value = true
  try {
    const res = await getDownloadUrl(doc.id)
    presignedUrl.value = res.data.url
  } catch {
    previewError.value = 'Could not load preview.'
  } finally {
    loadingPreview.value = false
  }
}

async function remove(id) {
  if (!confirm('Delete this report? This cannot be undone.')) return
  try {
    await deleteDocument(id)
    if (selectedId.value === id) {
      selectedDoc.value = null
      selectedId.value = null
      presignedUrl.value = ''
    }
    // Reload from server so the page backfills; step back if this page emptied
    await loadPage(page.value)
    if (documents.value.length === 0 && page.value > 0) {
      await loadPage(page.value - 1)
    }
  } catch {
    // non-critical: list remains intact if delete fails
  }
}

function relativeDate(iso) {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 60) return mins + 'm ago'
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return hrs + 'h ago'
  return Math.floor(hrs / 24) + 'd ago'
}
</script>

<style scoped>
.files-page { display: flex; flex-direction: column; }
.split {
  display: flex;
  flex: 1;
  height: calc(100vh - 120px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}

/* Left panel */
.doc-list {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--border);
  overflow-y: auto;
}
.list-hint { padding: 16px; color: var(--text-2); font-size: 13px; }
.doc-row {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border);
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.doc-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}
.doc-row:hover { background: var(--bg); }
.doc-row.selected {
  background: #f0f7ff;
  border-left: 3px solid var(--accent);
}
.badge {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  width: fit-content;
}
.badge.pdf   { background: #fee2e2; color: #b91c1c; }
.badge.excel { background: #dcfce7; color: #15803d; }
.badge.csv   { background: #e0f2fe; color: #0369a1; }
.doc-status { font-size: 12px; color: var(--text-2); }
.doc-date   { font-size: 11px; color: var(--text-2); }
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-2);
}
.pager-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 12px;
  cursor: pointer;
  color: var(--text-2);
}
.pager-btn:disabled { opacity: 0.4; cursor: default; }
.pager-btn:not(:disabled):hover { background: var(--bg); }

/* Right panel */
.preview-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.empty-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  font-size: 14px;
}
.preview-toolbar {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.preview-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pdf-frame {
  flex: 1;
  width: 100%;
  border: none;
}
.no-preview {
  padding: 48px;
  color: var(--text-2);
  text-align: center;
  font-size: 14px;
}
.error-msg { padding: 16px; color: #b91c1c; font-size: 13px; }
.doc-note { font-size: 13px; color: var(--text-2); font-style: italic; margin: 6px 0 0; }
.btn-remove {
  background: none; border: none; cursor: pointer;
  color: var(--text-2); font-size: 13px; padding: 2px 6px; border-radius: 4px;
  line-height: 1; flex-shrink: 0;
}
.btn-remove:hover { background: #fee2e2; color: #dc2626; }
</style>
