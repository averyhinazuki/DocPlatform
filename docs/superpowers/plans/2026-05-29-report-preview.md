# Report Preview Feature — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can click a report-ready notification and land on a redesigned Files page that shows a document list on the left and an inline PDF preview (or format fallback) on the right, with a Download button.

**Architecture:** Add `documentId` to the `Notification` MongoDB document so the bell can navigate to `/files?docId=xxx`. Add `GET /api/files` to return the tenant's document list. Redesign `FilesView` as a split panel — list on the left, preview on the right — auto-selecting the doc from the query param on arrival.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data MongoDB, Vue 3, Pinia, Vue Router, Axios, MinIO presigned URLs, browser-native PDF iframe

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/com/example/docplatform/document/Notification.java` | Modify | Add `documentId` field |
| `src/main/java/com/example/docplatform/notification/InAppNotificationService.java` | Modify | Accept `documentId` param in `send()` |
| `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java` | Modify | Pass `event.documentId()` to `inAppService.send()` |
| `src/main/java/com/example/docplatform/dto/file/DocumentSummary.java` | Create | DTO record for document list items |
| `src/main/java/com/example/docplatform/service/FileService.java` | Modify | Add `listByTenant(tenantId)` |
| `src/main/java/com/example/docplatform/controller/FileController.java` | Modify | Add `GET /api/files` endpoint |
| `src/test/java/com/example/docplatform/service/InAppNotificationServiceTest.java` | Create | Verify `documentId` is persisted |
| `src/test/java/com/example/docplatform/service/FileServiceTest.java` | Create | Verify `listByTenant` maps correctly |
| `frontend/src/api/files.js` | Modify | Add `listDocuments()` |
| `frontend/src/components/NotificationBell.vue` | Modify | Make report notifications clickable |
| `frontend/src/views/FilesView.vue` | Modify | Full split-panel redesign |

---

## Task 1: Add `documentId` to Notification and update InAppNotificationService

**Files:**
- Modify: `src/main/java/com/example/docplatform/document/Notification.java`
- Modify: `src/main/java/com/example/docplatform/notification/InAppNotificationService.java`
- Modify: `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java`
- Create: `src/test/java/com/example/docplatform/service/InAppNotificationServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/example/docplatform/service/InAppNotificationServiceTest.java`:

```java
package com.example.docplatform.service;

import com.example.docplatform.document.Notification;
import com.example.docplatform.entity.User;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.notification.InAppNotificationService;
import com.example.docplatform.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserMapper userMapper;
    @Mock RedissonClient redissonClient;
    @InjectMocks InAppNotificationService inAppNotificationService;

    private void stubRedisson() {
        RTopic mockTopic = mock(RTopic.class);
        when(redissonClient.getTopic(anyString())).thenReturn(mockTopic);
        when(mockTopic.publish(any())).thenReturn(0L);
    }

    private User stubUser() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectOne(any())).thenReturn(user);
        return user;
    }

    @Test
    void send_storesDocumentIdOnNotification() {
        stubRedisson();
        stubUser();

        inAppNotificationService.send(10L, List.of("user@test.com"), "Report ready", "doc-123");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDocumentId()).isEqualTo("doc-123");
    }

    @Test
    void send_allowsNullDocumentId() {
        stubRedisson();
        stubUser();

        inAppNotificationService.send(10L, List.of("user@test.com"), "Hello", null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDocumentId()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=InAppNotificationServiceTest -q
```

Expected: compilation error — `send()` does not yet accept 4 arguments.

- [ ] **Step 3: Add `documentId` field to `Notification`**

Open `src/main/java/com/example/docplatform/document/Notification.java` and add one field after `createdAt`:

```java
private String documentId;   // null for non-report notifications
```

Full file after change:
```java
package com.example.docplatform.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
public class Notification {
    @Id private String id;
    @Indexed private Long tenantId;
    @Indexed private Long userId;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
    private String documentId;
}
```

- [ ] **Step 4: Update `InAppNotificationService.send()` to accept `documentId`**

Replace the `send()` method body in `src/main/java/com/example/docplatform/notification/InAppNotificationService.java`:

```java
public void send(Long tenantId, List<String> recipientEmails, String message, String documentId) {
    recipientEmails.forEach(email -> {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, email));
        if (user == null) return;

        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setUserId(user.getId());
        n.setMessage(message);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        n.setDocumentId(documentId);
        notificationRepository.save(n);

        redissonClient.getTopic("notifications:" + tenantId).publish(message);
    });
}
```

- [ ] **Step 5: Update `NotificationConsumer` to pass `documentId`**

In `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java`, update the `inAppService.send()` call:

```java
String msg = "Report ready: " + event.documentId() + " (" + event.fileFormat() + ")";
inAppService.send(event.tenantId(), event.recipients(), msg, event.documentId());
```

Full file after change:
```java
package com.example.docplatform.kafka.consumer;

import com.example.docplatform.kafka.event.ReportCompletedEvent;
import com.example.docplatform.notification.EmailNotificationService;
import com.example.docplatform.notification.InAppNotificationService;
import com.example.docplatform.report.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final EmailNotificationService emailService;
    private final InAppNotificationService inAppService;
    private final DocumentStorageService storageService;

    @KafkaListener(topics = "report.completed", groupId = "docplatform-group")
    public void consume(ReportCompletedEvent event) {
        try {
            String downloadUrl = storageService.generatePresignedUrl(event.minioObjectKey());
            emailService.sendReportReady(event.recipients(), event.documentId(),
                event.fileFormat(), downloadUrl);
        } catch (Exception e) {
            log.warn("Email notification failed for document {} — in-app notification will still be sent: {}",
                event.documentId(), e.getMessage());
        }

        String msg = "Report ready: " + event.documentId() + " (" + event.fileFormat() + ")";
        inAppService.send(event.tenantId(), event.recipients(), msg, event.documentId());
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```
mvn test -pl . -Dtest=InAppNotificationServiceTest -q
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 7: Run full test suite to check for regressions**

```
mvn test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```
git add src/main/java/com/example/docplatform/document/Notification.java
git add src/main/java/com/example/docplatform/notification/InAppNotificationService.java
git add src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java
git add src/test/java/com/example/docplatform/service/InAppNotificationServiceTest.java
git commit -m "feat: add documentId to Notification and thread it through InAppNotificationService"
```

---

## Task 2: Document list API — DTO, FileService, FileController

**Files:**
- Create: `src/main/java/com/example/docplatform/dto/file/DocumentSummary.java`
- Modify: `src/main/java/com/example/docplatform/service/FileService.java`
- Modify: `src/main/java/com/example/docplatform/controller/FileController.java`
- Create: `src/test/java/com/example/docplatform/service/FileServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/example/docplatform/service/FileServiceTest.java`:

```java
package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.dto.file.DocumentSummary;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.report.storage.DocumentStorageService;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock GeneratedDocumentRepository documentRepository;
    @Mock DocumentStorageService storageService;
    @InjectMocks FileService fileService;

    @Test
    void listByTenant_returnsMappedSummaries() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setFileFormat(FileFormat.PDF);
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setGeneratedAt(LocalDateTime.of(2026, 5, 29, 10, 0));
        doc.setScheduleId(42L);

        when(documentRepository.findByTenantIdOrderByGeneratedAtDesc(1L)).thenReturn(List.of(doc));

        List<DocumentSummary> result = fileService.listByTenant(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("doc-1");
        assertThat(result.get(0).fileFormat()).isEqualTo(FileFormat.PDF);
        assertThat(result.get(0).status()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(result.get(0).generatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 29, 10, 0));
        assertThat(result.get(0).scheduleId()).isEqualTo(42L);
    }

    @Test
    void listByTenant_returnsEmptyListWhenNoDocs() {
        when(documentRepository.findByTenantIdOrderByGeneratedAtDesc(1L)).thenReturn(List.of());
        assertThat(fileService.listByTenant(1L)).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=FileServiceTest -q
```

Expected: compilation error — `DocumentSummary` and `listByTenant` do not exist yet.

- [ ] **Step 3: Create `DocumentSummary` DTO**

Create `src/main/java/com/example/docplatform/dto/file/DocumentSummary.java`:

```java
package com.example.docplatform.dto.file;

import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;

import java.time.LocalDateTime;

public record DocumentSummary(
    String id,
    FileFormat fileFormat,
    ReportStatus status,
    LocalDateTime generatedAt,
    Long scheduleId
) {}
```

- [ ] **Step 4: Add `listByTenant()` to `FileService`**

Add the following method to `src/main/java/com/example/docplatform/service/FileService.java` (add import for `DocumentSummary` and `List`):

```java
import com.example.docplatform.dto.file.DocumentSummary;
import java.util.List;
```

Method to add:
```java
public List<DocumentSummary> listByTenant(Long tenantId) {
    return documentRepository.findByTenantIdOrderByGeneratedAtDesc(tenantId).stream()
        .map(d -> new DocumentSummary(d.getId(), d.getFileFormat(), d.getStatus(),
                                     d.getGeneratedAt(), d.getScheduleId()))
        .toList();
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
mvn test -pl . -Dtest=FileServiceTest -q
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 6: Add `GET /api/files` to `FileController`**

Add the following method to `src/main/java/com/example/docplatform/controller/FileController.java`:

```java
import com.example.docplatform.dto.file.DocumentSummary;
import java.util.List;
```

Method to add (above the existing `getDownloadUrl` method):
```java
@GetMapping
public List<DocumentSummary> list(@AuthenticationPrincipal TenantUserDetails user) {
    return fileService.listByTenant(user.tenantId());
}
```

- [ ] **Step 7: Run full test suite**

```
mvn test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```
git add src/main/java/com/example/docplatform/dto/file/DocumentSummary.java
git add src/main/java/com/example/docplatform/service/FileService.java
git add src/main/java/com/example/docplatform/controller/FileController.java
git add src/test/java/com/example/docplatform/service/FileServiceTest.java
git commit -m "feat: add GET /api/files document list endpoint"
```

---

## Task 3: Frontend — listDocuments API + clickable NotificationBell

**Files:**
- Modify: `frontend/src/api/files.js`
- Modify: `frontend/src/components/NotificationBell.vue`

- [ ] **Step 1: Add `listDocuments()` to `frontend/src/api/files.js`**

Replace the entire file with:

```js
import api from './axios'
export const getDownloadUrl = (documentId) => api.get(`/files/${documentId}/url`)
export const listDocuments = () => api.get('/files')
```

- [ ] **Step 2: Update `NotificationBell.vue` to navigate on click**

Replace the full content of `frontend/src/components/NotificationBell.vue` with:

```vue
<template>
  <div class="bell-wrapper" @click="toggle">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
      <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
    </svg>
    <span v-if="notifStore.badgeCount > 0" class="badge">{{ notifStore.badgeCount }}</span>
    <div v-if="open" class="dropdown" @click.stop>
      <div class="dropdown-header">
        <span>Notifications</span>
      </div>
      <div v-if="notifStore.unread.length === 0" class="empty">No unread notifications</div>
      <div v-for="n in notifStore.unread" :key="n.id"
           class="notif-item"
           :class="{ clickable: n.documentId }"
           @click="n.documentId && navigate(n.documentId)">
        {{ n.message }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '../stores/notifications'

const notifStore = useNotificationStore()
const router = useRouter()
const open = ref(false)
let pollInterval = null

onMounted(() => {
  notifStore.fetch().catch(() => {})
  pollInterval = setInterval(() => notifStore.fetch().catch(() => {}), 15000)
})

onUnmounted(() => clearInterval(pollInterval))

function toggle() { open.value = !open.value }

function navigate(documentId) {
  open.value = false
  router.push('/files?docId=' + documentId)
}
</script>

<style scoped>
.bell-wrapper { position: relative; cursor: pointer; color: var(--text-2); display: flex; align-items: center; }
.badge {
  position: absolute; top: -6px; right: -8px;
  background: #ff3b30; color: white;
  font-size: 10px; font-weight: 700;
  min-width: 16px; height: 16px;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  padding: 0 4px;
}
.dropdown {
  position: absolute; top: 32px; right: -8px;
  background: white; border: 1px solid var(--border);
  border-radius: var(--radius); box-shadow: 0 8px 24px rgba(0,0,0,0.12);
  width: 280px; z-index: 200; overflow: hidden;
}
.dropdown-header {
  padding: 12px 16px;
  font-size: 13px; font-weight: 600;
  border-bottom: 1px solid var(--border);
  color: var(--text-2);
}
.empty { padding: 16px; color: var(--text-2); font-size: 13px; text-align: center; }
.notif-item { padding: 12px 16px; font-size: 13px; border-bottom: 1px solid var(--border); }
.notif-item:last-child { border-bottom: none; }
.notif-item.clickable { cursor: pointer; }
.notif-item.clickable:hover { background: var(--bg); }
</style>
```

- [ ] **Step 3: Commit**

```
git add frontend/src/api/files.js
git add frontend/src/components/NotificationBell.vue
git commit -m "feat: make report notifications clickable, add listDocuments API"
```

---

## Task 4: Redesign FilesView — split panel with document list and PDF preview

**Files:**
- Modify: `frontend/src/views/FilesView.vue`

- [ ] **Step 1: Replace `FilesView.vue` with the split-panel layout**

Replace the full content of `frontend/src/views/FilesView.vue` with:

```vue
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
</style>
```

- [ ] **Step 2: Commit**

```
git add frontend/src/views/FilesView.vue
git commit -m "feat: redesign FilesView as split panel with document list and PDF preview"
```

---

## Task 5: Update CHANGELOG

- [ ] **Step 1: Add entry to `docs/superpowers/plans/CHANGELOG.md`**

Add the following block at the top of the changelog (after the `# DocPlatform Changelog` heading):

```markdown
## 2026-05-29 — Report Preview & Document List

**Feature:** Clicking a report-ready notification navigates to the Files page with the document auto-selected and previewed. PDF reports render inline; Excel/CSV show a format-fallback message. The Files page is redesigned as a split panel (document list on the left, preview on the right) replacing the manual ID lookup.

**Backend files modified:**
- `src/main/java/com/example/docplatform/document/Notification.java` — added `documentId` field
- `src/main/java/com/example/docplatform/notification/InAppNotificationService.java` — added `documentId` param to `send()`
- `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java` — passes `event.documentId()` to inAppService
- `src/main/java/com/example/docplatform/service/FileService.java` — added `listByTenant()`
- `src/main/java/com/example/docplatform/controller/FileController.java` — added `GET /api/files`

**Backend files created:**
- `src/main/java/com/example/docplatform/dto/file/DocumentSummary.java`

**Tests created:**
- `src/test/java/com/example/docplatform/service/InAppNotificationServiceTest.java`
- `src/test/java/com/example/docplatform/service/FileServiceTest.java`

**Frontend files modified:**
- `frontend/src/api/files.js` — added `listDocuments()`
- `frontend/src/components/NotificationBell.vue` — clickable report notifications
- `frontend/src/views/FilesView.vue` — full split-panel redesign

---
```

- [ ] **Step 2: Commit**

```
git add docs/superpowers/plans/CHANGELOG.md
git commit -m "docs: update CHANGELOG for report preview feature"
```
