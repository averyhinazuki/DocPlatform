# Admin File Delete — Design

**Date:** 2026-05-29  
**Status:** Approved

## Overview

Admins can delete a generated document from the Files page. A `✕` button appears in each row of the document list (admin-only). Clicking it shows a confirmation prompt, then calls `DELETE /api/files/{documentId}`. The backend deletes the record from MongoDB and the file object from MinIO, both scoped to the caller's tenant.

## Frontend

**FilesView.vue**

- Import `useAuthStore` and check `authStore.role === 'ADMIN'` to conditionally render the delete button.
- Each `doc-row` gets a `✕` button on the right side (same `btn-remove` style as TemplatesView).
- `doc-row` layout: `flex-direction: row`, `align-items: center`, badges/status/date on the left (flex-column sub-container), button on the right.
- `remove(id)` handler: `confirm()` dialog → `deleteDocument(id)` API call → splice doc from `documents.value` → if deleted doc was selected, clear `selectedDoc` and `selectedId`.

**api/files.js**

- Add `deleteDocument(id)` → `api.delete(\`/files/${id}\`)`.

## Backend

**FileController**

- `DELETE /api/files/{documentId}` — authenticated, admin role enforced via manual role check on `TenantUserDetails` (consistent with the rest of the project; no `@PreAuthorize` used elsewhere).
- Returns `204 No Content` on success.

**FileService**

- `delete(Long tenantId, String documentId)`:
  1. Fetch doc from MongoDB; throw `ResourceNotFoundException` if missing.
  2. Check `doc.getTenantId().equals(tenantId)`; throw `TenantAccessDeniedException` if mismatch.
  3. Call `storageService.delete(doc.getMinioObjectKey())` to remove from MinIO.
  4. Call `documentRepository.deleteById(documentId)` to remove from MongoDB.

`DocumentStorageService.delete()` already exists — no changes needed there.

## Error Handling

- Missing doc → 404 (existing `ResourceNotFoundException` handler).
- Wrong tenant → 403 (existing `TenantAccessDeniedException` handler).
- Non-admin calling the endpoint → 403 (manual role check in controller).
- MinIO failure → 500; doc is NOT deleted from MongoDB (MinIO first, Mongo second preserves consistency — a partial failure leaves the record but removes the file; acceptable for admin cleanup).

## What Is Not Included

- No cascade deletion of notifications referencing the document (notifications remain but the download link becomes stale — acceptable).
- No soft delete / recycle bin.
- No bulk delete.
