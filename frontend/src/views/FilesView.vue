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
          <span class="badge" :class="doc.fileFormat.toLowerCase()">{{ doc.fileFormat }}</span>
          <span class="doc-status">{{ doc.status }}</span>
          <span class="doc-date">{{ relativeDate(doc.generatedAt) }}</span>
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
import { listDocuments, getDownloadUrl } from '../api/files'

const route = useRoute()

const documents = ref([])
const loadingList = ref(true)
const selectedDoc = ref(null)
const selectedId = ref(null)
const presignedUrl = ref('')
const loadingPreview = ref(false)
const previewError = ref('')

onMounted(async () => {
  try {
    const res = await listDocuments()
    documents.value = res.data
  } finally {
    loadingList.value = false
  }

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
  flex-direction: column;
  gap: 4px;
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
</style>
