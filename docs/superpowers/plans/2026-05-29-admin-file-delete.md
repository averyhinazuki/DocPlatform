# Admin File Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an admin-only delete button to the Files page that removes a document from both MongoDB and MinIO.

**Architecture:** New `FileService.delete()` enforces tenant ownership then removes the MinIO object and MongoDB record. The controller exposes `DELETE /api/files/{documentId}` gated by `@PreAuthorize("hasRole('ADMIN')")`. The frontend shows a `✕` button per row when the signed-in user is admin, following the exact same pattern as Template Delete.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security (`@PreAuthorize`), Spring Data MongoDB, MinIO (`DocumentStorageService`), Vue 3, Pinia, Axios

---

### Task 1: FileService — add `delete()` with tests

**Files:**
- Modify: `src/main/java/com/example/docplatform/service/FileService.java`
- Modify: `src/test/java/com/example/docplatform/service/FileServiceTest.java`

- [ ] **Step 1: Write three failing tests in FileServiceTest**

Add these tests inside `FileServiceTest` (the class already has `@ExtendWith(MockitoExtension.class)`, `@Mock` fields, and `@InjectMocks FileService fileService`):

```java
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@Test
void delete_removesFromStorageAndRepository() throws Exception {
    GeneratedDocument doc = new GeneratedDocument();
    doc.setId("doc-1"); doc.setTenantId(1L);
    doc.setMinioObjectKey("reports/1/file.pdf");
    when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

    fileService.delete(1L, "doc-1");

    verify(storageService).delete("reports/1/file.pdf");
    verify(documentRepository).deleteById("doc-1");
}

@Test
void delete_throwsResourceNotFoundWhenMissing() {
    when(documentRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> fileService.delete(1L, "missing"))
        .isInstanceOf(ResourceNotFoundException.class);
}

@Test
void delete_throwsTenantAccessDeniedForWrongTenant() {
    GeneratedDocument doc = new GeneratedDocument();
    doc.setId("doc-1"); doc.setTenantId(99L);
    doc.setMinioObjectKey("reports/99/file.pdf");
    when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

    assertThatThrownBy(() -> fileService.delete(1L, "doc-1"))
        .isInstanceOf(TenantAccessDeniedException.class);

    verify(storageService, never()).delete(Mockito.any());
    verify(documentRepository, never()).deleteById(Mockito.any());
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
mvn test -pl . -Dtest=FileServiceTest -q
```

Expected: 3 failures (method `delete` does not exist).

- [ ] **Step 3: Implement `FileService.delete()`**

Add this method to `src/main/java/com/example/docplatform/service/FileService.java`:

```java
public void delete(Long tenantId, String documentId) throws Exception {
    GeneratedDocument doc = documentRepository.findById(documentId)
        .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

    if (!doc.getTenantId().equals(tenantId)) {
        throw new TenantAccessDeniedException("Access denied to document: " + documentId);
    }

    storageService.delete(doc.getMinioObjectKey());
    documentRepository.deleteById(documentId);
}
```

Add the missing imports at the top of `FileService.java`:
```java
import com.example.docplatform.exception.ResourceNotFoundException;
```

(`TenantAccessDeniedException` is already imported from `getDownloadUrl`.)

- [ ] **Step 4: Run tests to confirm they pass**

```
mvn test -pl . -Dtest=FileServiceTest -q
```

Expected: `Tests run: 7, Failures: 0, Errors: 0` (4 existing + 3 new).

- [ ] **Step 5: Commit**

```
git add src/main/java/com/example/docplatform/service/FileService.java src/test/java/com/example/docplatform/service/FileServiceTest.java
git commit -m "feat: add FileService.delete() with tenant guard"
```

---

### Task 2: FileController — `DELETE /api/files/{documentId}`

**Files:**
- Modify: `src/main/java/com/example/docplatform/controller/FileController.java`

- [ ] **Step 1: Add the delete endpoint**

Open `FileController.java`. Add the following imports if not already present:

```java
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
```

Then add this method to `FileController`:

```java
@DeleteMapping("/{documentId}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> delete(
        @PathVariable String documentId,
        @AuthenticationPrincipal TenantUserDetails user) throws Exception {
    fileService.delete(user.tenantId(), documentId);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 2: Verify the project compiles**

```
mvn compile -q
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 3: Run the full test suite to confirm no regressions**

```
mvn test -q
```

Expected: all tests pass, 0 failures.

- [ ] **Step 4: Commit**

```
git add src/main/java/com/example/docplatform/controller/FileController.java
git commit -m "feat: add DELETE /api/files/{documentId} admin endpoint"
```

---

### Task 3: Frontend — `api/files.js` + delete button in FilesView

**Files:**
- Modify: `frontend/src/api/files.js`
- Modify: `frontend/src/views/FilesView.vue`

- [ ] **Step 1: Add `deleteDocument` to `api/files.js`**

The current file is:
```js
import api from './axios'
export const getDownloadUrl = (documentId) => api.get(`/files/${documentId}/url`)
export const listDocuments = () => api.get('/files')
```

Replace it with:
```js
import api from './axios'
export const getDownloadUrl = (documentId) => api.get(`/files/${documentId}/url`)
export const listDocuments = () => api.get('/files')
export const deleteDocument = (documentId) => api.delete(`/files/${documentId}`)
```

- [ ] **Step 2: Update FilesView.vue — script**

In `FilesView.vue`, update the `<script setup>` block:

1. Change the import line for `files` API to include `deleteDocument`:
```js
import { listDocuments, getDownloadUrl, deleteDocument } from '../api/files'
```

2. Add the auth store import after the `useRoute` import:
```js
import { useAuthStore } from '../stores/auth'
```

3. Add the store instantiation after `const route = useRoute()`:
```js
const authStore = useAuthStore()
```

4. Add the `remove` function after the `select` function:
```js
async function remove(id) {
  if (!confirm('Delete this report? This cannot be undone.')) return
  try {
    await deleteDocument(id)
    documents.value = documents.value.filter(d => d.id !== id)
    if (selectedId.value === id) {
      selectedDoc.value = null
      selectedId.value = null
      presignedUrl.value = ''
    }
  } catch {
    // non-critical: list remains intact if delete fails
  }
}
```

- [ ] **Step 3: Update FilesView.vue — template**

Find the `doc-row` block in the template:

```html
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
```

Replace it with:

```html
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
```

Note: `@click.stop` prevents the row's `select()` from firing when the delete button is clicked.

- [ ] **Step 4: Update FilesView.vue — styles**

In the `<style scoped>` block, update the `.doc-row` rule and add new rules:

Find:
```css
.doc-row {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
```

Replace with:
```css
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
```

Then append these rules at the end of the `<style scoped>` block (before `</style>`):

```css
.btn-remove {
  background: none; border: none; cursor: pointer;
  color: var(--text-2); font-size: 13px; padding: 2px 6px; border-radius: 4px;
  line-height: 1; flex-shrink: 0;
}
.btn-remove:hover { background: #fee2e2; color: #dc2626; }
```

- [ ] **Step 5: Verify the frontend builds without errors**

```
cd frontend && npm run build 2>&1 | tail -20
```

Expected: `✓ built in` with no errors.

- [ ] **Step 6: Commit**

```
git add frontend/src/api/files.js frontend/src/views/FilesView.vue
git commit -m "feat: admin delete button in Files page"
```
