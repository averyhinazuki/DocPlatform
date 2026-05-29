# Dynamic Params Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the raw `Params (JSON)` textarea in ReportsView with dynamic labeled text inputs — one per variable declared on the selected template.

**Architecture:** Frontend-only change to `ReportsView.vue`. Remove `paramsRaw` string ref and JSON parse logic; replace with a `paramsForm` reactive object keyed by variable name. A `humanize()` helper converts `camelCase`/`snake_case` variable names into readable labels. The existing `watch(selectedTemplateId)` is extended to reset `paramsForm` when the template changes.

**Tech Stack:** Vue 3, Pinia, Vue Router

---

## File Map

**Modify:**
- `frontend/src/views/ReportsView.vue` — replace params textarea with dynamic inputs

---

### Task 1: Replace params textarea with dynamic labeled inputs

**Files:**
- Modify: `frontend/src/views/ReportsView.vue`

- [ ] **Step 1: Remove `paramsRaw` and add `paramsForm` in the script**

In `frontend/src/views/ReportsView.vue`, find this line in the `<script setup>` block:

```js
const paramsRaw = ref('')
```

Replace it with:

```js
const paramsForm = reactive({})
```

- [ ] **Step 2: Extend the `watch` to reset `paramsForm` on template change**

Find the existing watch block:

```js
watch(selectedTemplateId, id => {
  const t = templates.value.find(t => t.id === id)
  if (t) { form.templateId = t.id; form.reportType = t.type }
})
```

Replace it with:

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

- [ ] **Step 3: Add the `humanize` helper function**

After the `watch` block, add:

```js
function humanize(key) {
  return key
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, c => c.toUpperCase())
}
```

- [ ] **Step 4: Update `submit()` to use `paramsForm` instead of parsing JSON**

Inside `submit()`, find and remove the entire JSON parse block:

```js
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
```

Replace it with:

```js
const params = { ...paramsForm }
```

- [ ] **Step 5: Replace the textarea in the template**

In the `<template>` block, find and remove:

```html
<div class="form-group">
  <label>Params (JSON)</label>
  <textarea v-model="paramsRaw" placeholder='{"region": "US"}'></textarea>
</div>
```

Replace it with:

```html
<div v-if="selectedTemplate?.variables?.length" class="form-group">
  <label>Report Data</label>
  <div class="params-grid">
    <div v-for="v in selectedTemplate.variables" :key="v" class="param-row">
      <label class="param-label">{{ humanize(v) }}</label>
      <input type="text" v-model="paramsForm[v]" :placeholder="v" />
    </div>
  </div>
</div>
```

- [ ] **Step 6: Add CSS for the params grid**

In the `<style scoped>` block, append:

```css
.params-grid { display: flex; flex-direction: column; gap: 10px; }
.param-row { display: grid; grid-template-columns: 140px 1fr; align-items: center; gap: 12px; }
.param-label { font-size: 13px; color: var(--text-2); font-weight: 500; }
```

- [ ] **Step 7: Start the dev server and verify**

```bash
cd frontend && npm run dev
```

Navigate to `http://localhost:5173/reports`.

Check the following:

1. **Template with variables** — Select a template that has variables (e.g. one with `variables: ["region", "year"]`). Confirm:
   - A "Report Data" label appears
   - One labeled input per variable renders beneath it
   - Labels are human-readable (`salesTotal` → "Sales Total", `region` → "Region")
   - Inputs are empty text fields

2. **Template with no variables** — Select a template that has an empty `variables` array. Confirm:
   - No "Report Data" section is rendered at all

3. **Switching templates** — Select a template with variables, type values into the inputs, then switch to a different template. Confirm:
   - The inputs reset to empty for the new template's variables

4. **Assignment mode** — Navigate to `/reports?assignmentId=1&templateId=<real-id>`. Confirm:
   - Template is pre-selected and disabled
   - The variable inputs for that template appear immediately on load (no need to select a template)

5. **Submit** — Fill in some variable values and click Generate. Confirm:
   - Request succeeds (202 Accepted with `documentId`)
   - No JSON parse errors

- [ ] **Step 8: Commit**

```bash
git add frontend/src/views/ReportsView.vue
git commit -m "feat: replace params JSON textarea with dynamic per-variable inputs"
```

---

### Task 2: Update CHANGELOG

**Files:**
- Modify: `docs/superpowers/plans/CHANGELOG.md`

- [ ] **Step 1: Prepend entry to CHANGELOG**

Open `docs/superpowers/plans/CHANGELOG.md` and add this block at the top (after the `# DocPlatform Changelog` heading):

```markdown
## 2026-05-29 — Dynamic Params Form

**Feature:** The raw `Params (JSON)` textarea in ReportsView is replaced by a dynamic list of labeled text inputs — one per variable declared on the selected template. Switching templates resets the inputs. Templates with no variables show nothing. Works in both one-off and assignment modes.

**Frontend files modified:**
- `frontend/src/views/ReportsView.vue` — replaced paramsRaw textarea with paramsForm reactive object and per-variable inputs
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/CHANGELOG.md
git commit -m "docs: update CHANGELOG for dynamic params form"
```
