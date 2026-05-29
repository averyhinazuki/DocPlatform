# Rich Text Editor for No-Parameter PDF Templates

**Date:** 2026-05-29
**Status:** Approved

---

## Goal

Allow admins to compose static PDF content using a Rich Text Editor (RTE) when creating a template with no variables. When a user generates a PDF report from such a template, the RTE is pre-filled with the admin's content and the user can edit it before submitting. The edited HTML travels through the pipeline as `contentOverride` and is rendered into PDF instead of the stored template HTML.

---

## Scope

- No-variable templates only. Templates with variables continue using `buildPdfTemplate` (auto-generated Thymeleaf table).
- PDF format only. CSV and Excel generators are unaffected.
- Two screens: template creation (admin) and report generation (user).

---

## Library

**Quill** via `@vueup/vue-quill` (npm packages: `quill`, `@vueup/vue-quill`).

Chosen over TipTap because it ships its own toolbar and theme CSS — no custom toolbar styling needed.

### Toolbar (both screens — identical)

Bold · Italic · Underline · H1 · H2 · Blockquote · Horizontal rule · Bullet list · Numbered list

---

## Architecture

### HTML wrapping

Quill produces HTML fragments (e.g. `<h2>Title</h2><p>...</p>`). Before storing or sending, the frontend wraps the fragment in a full document:

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8"/>
  <style>
    body { font-family: Arial, sans-serif; margin: 32px; color: #222; line-height: 1.6; }
    h1, h2 { margin-bottom: 12px; }
    ul, ol { padding-left: 20px; }
    blockquote { border-left: 3px solid #cbd5e1; margin: 0; padding-left: 14px; color: #64748b; }
    hr { border: none; border-top: 1px solid #e2e8f0; margin: 16px 0; }
  </style>
</head>
<body>
  {quill_html}
</body>
</html>
```

This wrapper is defined in a shared helper `wrapHtml(fragment)` used in both `TemplatesView.vue` and `ReportsView.vue`.

---

## Frontend Changes

### `package.json`
Add dependencies:
- `quill` (^2.x)
- `@vueup/vue-quill` (^1.x)

### `api/templates.js`
Add:
```js
export const getTemplate = (id) => api.get(`/templates/${id}`)
```
Returns `{ id, name, type, variables, thymeleafTemplate }`.

### `TemplatesView.vue`

**Condition:** when `variablesRaw` is empty (admin left variables blank), show a Quill editor labelled "Content" instead of nothing.

**On submit:**
- If variables is empty → `thymeleafTemplate = wrapHtml(quill.getHTML())`
- If variables is non-empty → `thymeleafTemplate = buildPdfTemplate(form.name, variables)` (unchanged)

The Quill editor is reset to empty when the form resets after a successful create.

### `ReportsView.vue`

**Condition:** selected template has no variables (`selectedTemplate.variables.length === 0`) AND `form.format === 'PDF'`.

**Behaviour:**
1. When this condition becomes true (template selected or format switched to PDF), call `getTemplate(selectedTemplateId)` to fetch the HTML content.
2. Pre-fill the Quill editor with the fetched `thymeleafTemplate` content (strip the wrapper `<html>…<body>` tags to get the fragment back for Quill).
3. The RTE section replaces the "Report Data" params section for this case (they cannot coexist — no-variable templates have no params inputs).
4. On submit, `contentOverride = wrapHtml(quill.getHTML())` is added to the payload.

**Edge cases:**
- If the user switches to a template with variables, hide the RTE and show params inputs as normal.
- If the user switches format away from PDF while a no-variable template is selected, hide the RTE (CSV/Excel don't use it).
- If `getTemplate` fails, show a non-blocking warning; submit still works (falls back to stored template HTML).

### `api/reports.js`
Include `contentOverride` in the JSON payload blob (null when not applicable).

---

## Backend Changes

### `TemplateController`
Add endpoint:
```
GET /api/templates/{id}
```
- Auth: any authenticated user (needed by `ReportsView` to pre-fill the RTE).
- Returns: `{ id, name, type, variables, thymeleafTemplate }` for templates belonging to the caller's tenant. Returns 404 if not found or wrong tenant.

### `ReportRequest`
Add nullable field:
```java
String contentOverride
```

### `ReportRequestedEvent`
Add nullable field:
```java
String contentOverride
```

### `ReportService`
Pass `req.contentOverride()` into the `ReportRequestedEvent`.

### `PdfReportGenerator`
Priority logic:
1. If `params` contains a non-blank `contentOverride` key → use it as the HTML (skip Thymeleaf entirely, pass directly to `PdfRendererBuilder`).
2. Otherwise → existing path: render `template.getThymeleafTemplate()` through Thymeleaf.

Wait — `contentOverride` travels as a top-level event field, not inside `params`. `PdfReportGenerator.generate(template, params)` only receives `params`. Two options:
- Pass `contentOverride` inside `params` under a reserved key (e.g. `__contentOverride`).
- Change `ReportGenerator.generate()` signature to accept the full event.

**Decision:** pass it inside `params` under the reserved key `"__content"`. This avoids changing the generator interface. `ReportJobConsumer` merges it into the params map before calling `generator.generate()`. `CsvReportGenerator` and `ExcelReportGenerator` ignore unknown keys, so no impact.

`PdfReportGenerator` checks `params.get("__content")` first; if non-blank, uses it directly without Thymeleaf processing.

---

## Data Flow Summary

```
Admin (TemplatesView) → Quill HTML → wrapHtml() → thymeleafTemplate (stored in MongoDB)

User (ReportsView, no-variable PDF)
  → getTemplate(id) → pre-fill Quill
  → user edits
  → submit: contentOverride = wrapHtml(quill.getHTML())
  → ReportRequest.contentOverride
  → ReportRequestedEvent.contentOverride
  → ReportJobConsumer injects into params as "__content"
  → PdfReportGenerator: params["__content"] present → skip Thymeleaf → PDF
```

---

## Out of Scope

- Editing an existing template's RTE content (no template edit endpoint exists yet).
- Image upload inside the RTE.
- RTE for CSV or Excel output formats.
- RTE for templates that have variables.
