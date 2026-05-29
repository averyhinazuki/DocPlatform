---
name: dynamic-params-form
description: Replace the raw JSON params textarea in ReportsView with dynamic labeled text inputs per template variable
metadata:
  type: spec
  status: approved
  date: 2026-05-29
---

# Dynamic Params Form — Design Spec

**Date:** 2026-05-29
**Status:** Approved

---

## Problem

`ReportsView` shows `Params: region, year, salesTotal` as a hint but then asks the user to type `{"region": "US", "year": "2026"}` as raw JSON. This is developer-facing input and breaks the intended UX: a user filling in business data (sales figures, user counts, etc.) to inject into a Thymeleaf template.

---

## Solution

Replace the single `Params (JSON)` textarea with a dynamic list of labeled text inputs — one per variable declared on the selected template.

---

## Design

### Component change — `frontend/src/views/ReportsView.vue`

**Template section:**

Remove the `<div class="form-group">` block containing the `Params (JSON)` textarea. Replace it with:

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

No params section is rendered when the template has no variables.

**Script section:**

- Remove: `paramsRaw` ref and the JSON parse block inside `submit()`
- Add: `paramsForm` as a `reactive({})` object
- Extend `watch(selectedTemplateId, ...)` to reset `paramsForm`: clear all keys and re-populate with empty strings for each variable in the newly selected template
- Add helper: `function humanize(key)` — splits camelCase/snake_case into title-cased words (`salesTotal` → "Sales Total", `region` → "Region")
- In `submit()`, pass `paramsForm` directly as `params` (already a plain object matching `Map<String, Object>`)

**Style section:**

```css
.params-grid { display: flex; flex-direction: column; gap: 10px; }
.param-row { display: grid; grid-template-columns: 140px 1fr; align-items: center; gap: 12px; }
.param-label { font-size: 13px; color: var(--text-2); font-weight: 500; }
```

---

## Scope

- **Files changed:** `frontend/src/views/ReportsView.vue` only
- **Backend:** No changes. `ReportRequest.params` is already `Map<String, Object>`.
- **Tests:** No new tests required (pure presentational change; existing backend tests unaffected).

---

## Behavior

| Scenario | Result |
|---|---|
| Template with variables selected | One labeled text input per variable |
| Template with no variables | Params section hidden |
| Template not yet loaded | Params section hidden |
| Switching templates | All param inputs reset to empty |
| Assignment mode (template pre-locked) | Variable inputs appear immediately on load |

---

## Non-goals

- Typed inputs (number, date) — all variables treated as text strings for now
- Custom/extra params beyond what the template declares — not in scope
- Backend changes — none needed
