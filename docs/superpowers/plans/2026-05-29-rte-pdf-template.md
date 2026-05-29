# RTE for No-Parameter PDF Templates — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Rich Text Editor (Quill) to template creation (admin) and report generation (user) for PDF templates that have no variables; the user's edited HTML overrides the stored template at generation time via a `contentOverride` field.

**Architecture:** A reusable `RteEditor.vue` component wraps Quill with a standard toolbar. In `TemplatesView`, it replaces the invisible auto-generate step when variables are empty. In `ReportsView`, it appears when a no-variable PDF template is selected, pre-filled from a new `GET /api/templates/{id}` endpoint. The HTML travels through the pipeline as `contentOverride` on `ReportRequest` → `ReportRequestedEvent`, and `ReportJobConsumer` injects it into params as `"__content"` before calling `PdfReportGenerator`, which checks that key first and bypasses Thymeleaf.

**Tech Stack:** Vue 3, Vite, `@vueup/vue-quill@^1.0.0` (wraps Quill 1.x), Java 21, Spring Boot 3, Kafka

---

## File Map

**Create:**
- `frontend/src/components/RteEditor.vue` — reusable Quill wrapper with standard toolbar
- `frontend/src/utils/htmlTemplate.js` — `wrapHtml(fragment)` and `extractBody(html)` helpers
- `src/main/java/com/example/docplatform/dto/template/TemplateDetailResponse.java` — DTO for the detail endpoint (includes `thymeleafTemplate`)

**Modify:**
- `frontend/package.json` — add quill deps
- `frontend/src/api/templates.js` — add `getTemplate(id)`
- `frontend/src/api/reports.js` — include `contentOverride` in payload
- `frontend/src/views/TemplatesView.vue` — show RteEditor when variables empty
- `frontend/src/views/ReportsView.vue` — show pre-filled RteEditor for no-variable PDF
- `src/main/java/com/example/docplatform/controller/TemplateController.java` — add `GET /api/templates/{id}`
- `src/main/java/com/example/docplatform/dto/report/ReportRequest.java` — add `contentOverride`
- `src/main/java/com/example/docplatform/kafka/event/ReportRequestedEvent.java` — add `contentOverride`
- `src/main/java/com/example/docplatform/service/ReportService.java` — pass `contentOverride` into event
- `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java` — inject `"__content"` into params
- `src/main/java/com/example/docplatform/report/generator/PdfReportGenerator.java` — check `"__content"` first
- `src/main/java/com/example/docplatform/scheduler/ReportScheduler.java` — add null arg for new field
- `src/test/java/com/example/docplatform/service/ReportServiceTest.java` — update 8-arg constructor calls to 9

---

### Task 1: Install packages and create shared utilities

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/src/utils/htmlTemplate.js`

- [ ] **Step 1: Install npm packages**

```bash
cd frontend && npm install quill@^1.3.7 @vueup/vue-quill@^1.0.0
```

Expected: packages added to `node_modules`, `package.json` updated with both deps.

- [ ] **Step 2: Create `htmlTemplate.js`**

Create `frontend/src/utils/htmlTemplate.js`:

```js
export function wrapHtml(fragment) {
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8"/>
  <style>
    body { font-family: Arial, sans-serif; margin: 32px; color: #222; line-height: 1.6; }
    h1, h2 { margin-bottom: 12px; }
    ul, ol { padding-left: 20px; }
    blockquote { border-left: 3px solid #cbd5e1; margin: 0 0 0 0; padding-left: 14px; color: #64748b; }
    hr { border: none; border-top: 1px solid #e2e8f0; margin: 16px 0; }
  </style>
</head>
<body>
${fragment}
</body>
</html>`
}

export function extractBody(html) {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return doc.body.innerHTML
}
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add package.json package-lock.json
cd .. && git add frontend/src/utils/htmlTemplate.js
git commit -m "feat: add quill deps and htmlTemplate utility"
```

---

### Task 2: Create `RteEditor.vue`

**Files:**
- Create: `frontend/src/components/RteEditor.vue`

- [ ] **Step 1: Create the component**

Create `frontend/src/components/RteEditor.vue`:

```vue
<template>
  <div class="rte-wrap">
    <div :id="toolbarId">
      <span class="ql-formats">
        <button class="ql-bold"></button>
        <button class="ql-italic"></button>
        <button class="ql-underline"></button>
      </span>
      <span class="ql-formats">
        <button class="ql-header" value="1"></button>
        <button class="ql-header" value="2"></button>
      </span>
      <span class="ql-formats">
        <button class="ql-blockquote"></button>
        <button class="ql-hr-btn" @mousedown.prevent="insertHr" title="Horizontal rule">—</button>
      </span>
      <span class="ql-formats">
        <button class="ql-list" value="bullet"></button>
        <button class="ql-list" value="ordered"></button>
      </span>
    </div>
    <QuillEditor
      ref="editorRef"
      v-model:content="html"
      content-type="html"
      :toolbar="`#${toolbarId}`"
      theme="snow"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { QuillEditor } from '@vueup/vue-quill'
import Quill from 'quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

// Register HR blot once (Quill.register warns but does not throw on duplicates)
const BlockEmbed = Quill.import('blots/block/embed')
class DividerBlot extends BlockEmbed {}
DividerBlot.blotName = 'divider'
DividerBlot.tagName = 'hr'
Quill.register(DividerBlot, /* overwrite= */ true)

const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const toolbarId = `rte-tb-${Math.random().toString(36).slice(2, 8)}`
const editorRef = ref(null)
const html = ref(props.modelValue)

watch(() => props.modelValue, val => { if (val !== html.value) html.value = val })
watch(html, val => emit('update:modelValue', val))

function insertHr() {
  const quill = editorRef.value.getQuill()
  const range = quill.getSelection(true)
  quill.insertEmbed(range.index, 'divider', true, 'user')
  quill.setSelection(range.index + 1, 0)
}
</script>

<style scoped>
.rte-wrap {
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 6px;
  overflow: hidden;
}
.ql-hr-btn {
  font-size: 14px;
  line-height: 1;
  padding: 3px 6px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/RteEditor.vue
git commit -m "feat: add RteEditor component (Quill, standard toolbar)"
```

---

### Task 3: Backend — `TemplateDetailResponse` DTO and `GET /api/templates/{id}`

**Files:**
- Create: `src/main/java/com/example/docplatform/dto/template/TemplateDetailResponse.java`
- Modify: `src/main/java/com/example/docplatform/controller/TemplateController.java`

- [ ] **Step 1: Create `TemplateDetailResponse.java`**

Create `src/main/java/com/example/docplatform/dto/template/TemplateDetailResponse.java`:

```java
package com.example.docplatform.dto.template;

import java.util.List;

public record TemplateDetailResponse(
    String id,
    String name,
    String type,
    List<String> variables,
    String thymeleafTemplate
) {}
```

- [ ] **Step 2: Add the endpoint to `TemplateController.java`**

Open `src/main/java/com/example/docplatform/controller/TemplateController.java`. After the `@GetMapping` list endpoint, add:

```java
@GetMapping("/{id}")
public ResponseEntity<TemplateDetailResponse> getById(
        @PathVariable String id,
        @AuthenticationPrincipal TenantUserDetails user) {
    return templateRepository.findById(id)
        .filter(t -> t.getTenantId().equals(user.tenantId()))
        .map(t -> ResponseEntity.ok(new TemplateDetailResponse(
            t.getId(), t.getName(), t.getType(),
            t.getVariables(), t.getThymeleafTemplate())))
        .orElse(ResponseEntity.notFound().build());
}
```

The full file should now look like:

```java
package com.example.docplatform.controller;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.dto.template.TemplateDetailResponse;
import com.example.docplatform.dto.template.TemplateRequest;
import com.example.docplatform.dto.template.TemplateResponse;
import com.example.docplatform.repository.ReportTemplateRepository;
import com.example.docplatform.security.TenantUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final ReportTemplateRepository templateRepository;

    @GetMapping
    public List<TemplateResponse> list(@AuthenticationPrincipal TenantUserDetails user) {
        return templateRepository.findByTenantId(user.tenantId()).stream()
            .map(t -> new TemplateResponse(t.getId(), t.getName(), t.getType(), t.getVariables()))
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateDetailResponse> getById(
            @PathVariable String id,
            @AuthenticationPrincipal TenantUserDetails user) {
        return templateRepository.findById(id)
            .filter(t -> t.getTenantId().equals(user.tenantId()))
            .map(t -> ResponseEntity.ok(new TemplateDetailResponse(
                t.getId(), t.getName(), t.getType(),
                t.getVariables(), t.getThymeleafTemplate())))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal TenantUserDetails user) {
        templateRepository.findById(id).ifPresent(t -> {
            if (t.getTenantId().equals(user.tenantId())) {
                templateRepository.deleteById(id);
            }
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody TemplateRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        ReportTemplate t = new ReportTemplate();
        t.setTenantId(user.tenantId());
        t.setName(req.name());
        t.setType(req.type());
        t.setThymeleafTemplate(req.thymeleafTemplate());
        t.setVariables(req.variables() != null ? req.variables() : List.of());
        t.setCreatedAt(LocalDateTime.now());
        templateRepository.save(t);
        return ResponseEntity.status(201)
            .body(new TemplateResponse(t.getId(), t.getName(), t.getType(), t.getVariables()));
    }
}
```

- [ ] **Step 3: Add `getTemplate` to the frontend API**

In `frontend/src/api/templates.js`, add one line:

```js
import api from './axios'
export const listTemplates   = () => api.get('/templates')
export const createTemplate  = (data) => api.post('/templates', data)
export const deleteTemplate  = (id) => api.delete(`/templates/${id}`)
export const getTemplate     = (id) => api.get(`/templates/${id}`)
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/docplatform/dto/template/TemplateDetailResponse.java \
        src/main/java/com/example/docplatform/controller/TemplateController.java \
        frontend/src/api/templates.js
git commit -m "feat: add GET /api/templates/{id} and getTemplate() API helper"
```

---

### Task 4: Backend pipeline — `contentOverride` through `ReportRequest` → `PdfReportGenerator`

**Files:**
- Modify: `src/main/java/com/example/docplatform/dto/report/ReportRequest.java`
- Modify: `src/main/java/com/example/docplatform/kafka/event/ReportRequestedEvent.java`
- Modify: `src/main/java/com/example/docplatform/service/ReportService.java`
- Modify: `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java`
- Modify: `src/main/java/com/example/docplatform/report/generator/PdfReportGenerator.java`

- [ ] **Step 1: Add `contentOverride` to `ReportRequest.java`**

Replace the entire file content:

```java
package com.example.docplatform.dto.report;

import com.example.docplatform.enums.FileFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record ReportRequest(
    Long scheduleId,
    @NotBlank String reportType,
    @NotNull FileFormat format,
    @NotBlank String templateId,
    Map<String, Object> params,
    List<String> recipients,
    Long assignmentId,
    String note,
    String contentOverride
) {}
```

- [ ] **Step 2: Add `contentOverride` to `ReportRequestedEvent.java`**

Replace the entire file content:

```java
package com.example.docplatform.kafka.event;

import java.util.List;
import java.util.Map;

public record ReportRequestedEvent(
    String documentId,
    Long tenantId,
    Long scheduleId,
    String reportType,
    String fileFormat,
    String templateId,
    Map<String, Object> params,
    List<String> recipients,
    String triggeredBy,
    String note,
    String contentOverride
) {}
```

- [ ] **Step 3: Update `ReportService.java` to pass `contentOverride` into the event**

Replace the `producer.publishRequest(...)` call:

```java
producer.publishRequest(new ReportRequestedEvent(
    doc.getId(), tenantId, req.scheduleId(),
    req.reportType(), req.format().name(),
    req.templateId(), req.params(), req.recipients(),
    triggeredBy, req.note(), req.contentOverride()
));
```

- [ ] **Step 4: Update `ReportJobConsumer.java` to inject `"__content"` into params**

Find the line:
```java
byte[] content = generator.generate(template, event.params());
```

Replace with:

```java
Map<String, Object> params = event.params() != null
    ? new java.util.HashMap<>(event.params())
    : new java.util.HashMap<>();
if (event.contentOverride() != null && !event.contentOverride().isBlank()) {
    params.put("__content", event.contentOverride());
}
byte[] content = generator.generate(template, params);
```

Add these two imports to `ReportJobConsumer.java` (the existing file has `import java.util.List;` but not `HashMap` or `Map`):

```java
import java.util.HashMap;
import java.util.Map;
```

- [ ] **Step 5: Update `PdfReportGenerator.java` to check `"__content"` first**

Replace the entire `generate` method:

```java
@Override
public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
    Object override = params.get("__content");
    String html;
    if (override != null && !override.toString().isBlank()) {
        html = override.toString();
    } else {
        String templateContent = template.getThymeleafTemplate();
        if (templateContent == null || templateContent.isBlank()) {
            throw new IllegalStateException(
                "Template '" + template.getName() + "' has no HTML content. " +
                "Re-create the template via the Templates page to generate its HTML.");
        }
        Context ctx = new Context();
        ctx.setVariables(params);
        html = templateEngine.process(templateContent, ctx);
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PdfRendererBuilder builder = new PdfRendererBuilder();
    builder.withHtmlContent(html, null);
    builder.toStream(out);
    builder.run();
    return out.toByteArray();
}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/docplatform/dto/report/ReportRequest.java \
        src/main/java/com/example/docplatform/kafka/event/ReportRequestedEvent.java \
        src/main/java/com/example/docplatform/service/ReportService.java \
        src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java \
        src/main/java/com/example/docplatform/report/generator/PdfReportGenerator.java
git commit -m "feat: add contentOverride pipeline from ReportRequest to PdfReportGenerator"
```

---

### Task 5: Fix callsites — `ReportScheduler` and `ReportServiceTest`

**Files:**
- Modify: `src/main/java/com/example/docplatform/scheduler/ReportScheduler.java`
- Modify: `src/test/java/com/example/docplatform/service/ReportServiceTest.java`

- [ ] **Step 1: Add `null` for `contentOverride` in `ReportScheduler.java`**

Find:
```java
new ReportRequest(
    s.getId(), s.getReportType(), s.getFormat(),
    s.getTemplateId(), s.getParams(), s.getRecipients(), null, null)
```

Replace with:
```java
new ReportRequest(
    s.getId(), s.getReportType(), s.getFormat(),
    s.getTemplateId(), s.getParams(), s.getRecipients(), null, null, null)
```

- [ ] **Step 2: Update both `ReportRequest` constructors in `ReportServiceTest.java`**

Find (first occurrence):
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of("a@b.com"), null, null)
```
Replace with:
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of("a@b.com"), null, null, null)
```

Find (second occurrence):
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of(), null, null)
```
Replace with:
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of(), null, null, null)
```

- [ ] **Step 3: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=ReportServiceTest -q
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/docplatform/scheduler/ReportScheduler.java \
        src/test/java/com/example/docplatform/service/ReportServiceTest.java
git commit -m "fix: update ReportRequest callsites for new contentOverride field"
```

---

### Task 6: Frontend — `TemplatesView.vue` with RteEditor

**Files:**
- Modify: `frontend/src/views/TemplatesView.vue`

- [ ] **Step 1: Add `rteContent` ref, import `RteEditor` and `wrapHtml`**

In `<script setup>`, add imports and the new ref after the existing ones:

```js
import RteEditor from '../components/RteEditor.vue'
import { wrapHtml } from '../utils/htmlTemplate.js'
// ...
const rteContent = ref('')
```

- [ ] **Step 2: Add the RteEditor to the template**

In the `<template>` block, after the Variables form-group div and before the submit button, add:

```html
<div v-if="!variablesRaw.trim()" class="form-group">
  <label>Content <span class="hint">(PDF body — compose the document here)</span></label>
  <RteEditor v-model="rteContent" />
</div>
```

- [ ] **Step 3: Update `submit()` to use RTE content when variables are empty**

Find in `submit()`:
```js
const thymeleafTemplate = buildPdfTemplate(form.name, variables)
```

Replace with:
```js
const thymeleafTemplate = variables.length === 0
  ? wrapHtml(rteContent.value)
  : buildPdfTemplate(form.name, variables)
```

- [ ] **Step 4: Reset `rteContent` after successful create**

After `createSuccess.value = true` and before `await load()`, add:
```js
rteContent.value = ''
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/TemplatesView.vue
git commit -m "feat: show RteEditor in template creation when no variables"
```

---

### Task 7: Frontend — `ReportsView.vue` + `api/reports.js`

**Files:**
- Modify: `frontend/src/views/ReportsView.vue`
- Modify: `frontend/src/api/reports.js`

- [ ] **Step 1: Update `api/reports.js` to include `contentOverride`**

The file currently sends all payload keys. Since `contentOverride` will be a key on the payload object, it is already included automatically. No change is needed — just confirm:

```js
import api from './axios'

export const generateReport = (payload, file = null) => {
  const fd = new FormData()
  fd.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  if (file) fd.append('file', file)
  return api.post('/reports/generate', fd)
}
```

`payload` will contain `contentOverride` when the caller includes it — nothing to change here.

- [ ] **Step 2: Add `rteContent` ref, `isRteMode` computed, imports in `ReportsView.vue`**

In `<script setup>`, add:

```js
import RteEditor from '../components/RteEditor.vue'
import { wrapHtml, extractBody } from '../utils/htmlTemplate.js'
import { getTemplate } from '../api/templates'
// ...
const rteContent = ref('')
const isRteMode = computed(() =>
  selectedTemplate.value !== null &&
  (selectedTemplate.value.variables?.length ?? 0) === 0 &&
  form.format === 'PDF'
)
```

- [ ] **Step 3: Extend the existing `watch(selectedTemplateId, ...)` to pre-fill the RTE**

Find:
```js
watch(selectedTemplateId, id => {
  const t = templates.value.find(t => t.id === id)
  if (t) {
    form.templateId = t.id
    form.reportType = t.type
    Object.keys(paramsForm).forEach(k => delete paramsForm[k])
    if (t.variables) t.variables.forEach(v => { paramsForm[v] = '' })
  }
})
```

Replace with:
```js
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
    } catch { /* non-critical — user can write from scratch */ }
  }
})
```

- [ ] **Step 4: Add a watch on `form.format` to re-evaluate RTE mode**

After the `selectedTemplateId` watch, add:

```js
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
```

- [ ] **Step 5: Add the RteEditor section to the template**

In the `<template>` block, replace the existing Report Data section:

```html
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
```

- [ ] **Step 6: Include `contentOverride` in `submit()`**

Find in `submit()`:
```js
const payload = {
  ...form,
  params,
  recipients: selectedRecipients.value,
  ...(assignmentMode.value ? { assignmentId: assignmentId.value, scheduleId: null } : {})
}
```

Replace with:
```js
const payload = {
  ...form,
  params,
  recipients: selectedRecipients.value,
  contentOverride: isRteMode.value ? wrapHtml(rteContent.value) : null,
  ...(assignmentMode.value ? { assignmentId: assignmentId.value, scheduleId: null } : {})
}
```

- [ ] **Step 7: Commit**

```bash
git add frontend/src/views/ReportsView.vue frontend/src/api/reports.js
git commit -m "feat: show pre-filled RteEditor in report generation for no-variable PDF templates"
```

---

### Task 8: CHANGELOG + final commit

**Files:**
- Modify: `docs/superpowers/plans/CHANGELOG.md`

- [ ] **Step 1: Prepend entry**

Add at the top of `CHANGELOG.md` (after the heading):

```markdown
## 2026-05-29 — Rich Text Editor for No-Parameter PDF Templates

**Feature:** Admins can now compose static PDF content using a Rich Text Editor (Quill, standard toolbar) when creating a template with no variables. When a user generates a PDF report from such a template, the editor is pre-filled with the admin's content and the user can edit it before submitting. The edited HTML travels as `contentOverride` through the Kafka pipeline and is used directly by `PdfReportGenerator`, bypassing Thymeleaf.

**Toolbar:** Bold · Italic · Underline · H1 · H2 · Blockquote · Horizontal rule · Bullet list · Numbered list

**Backend files modified:**
- `src/main/java/com/example/docplatform/dto/report/ReportRequest.java` — added `contentOverride`
- `src/main/java/com/example/docplatform/kafka/event/ReportRequestedEvent.java` — added `contentOverride`
- `src/main/java/com/example/docplatform/service/ReportService.java` — passes `contentOverride` into event
- `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java` — injects `"__content"` into params when present
- `src/main/java/com/example/docplatform/report/generator/PdfReportGenerator.java` — checks `"__content"` first, bypasses Thymeleaf when set
- `src/main/java/com/example/docplatform/scheduler/ReportScheduler.java` — null for new field
- `src/main/java/com/example/docplatform/controller/TemplateController.java` — added `GET /api/templates/{id}`

**Backend files created:**
- `src/main/java/com/example/docplatform/dto/template/TemplateDetailResponse.java`

**Frontend files created:**
- `frontend/src/components/RteEditor.vue` — reusable Quill editor with standard toolbar
- `frontend/src/utils/htmlTemplate.js` — `wrapHtml()` and `extractBody()` helpers

**Frontend files modified:**
- `frontend/src/api/templates.js` — added `getTemplate(id)`
- `frontend/src/views/TemplatesView.vue` — shows RteEditor when variables empty
- `frontend/src/views/ReportsView.vue` — shows pre-filled RteEditor for no-variable PDF

**Tests modified:**
- `src/test/java/com/example/docplatform/service/ReportServiceTest.java` — updated 9-arg `ReportRequest` constructors
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/CHANGELOG.md docs/superpowers/plans/2026-05-29-rte-pdf-template.md
git commit -m "docs: update CHANGELOG for RTE PDF template feature"
```
