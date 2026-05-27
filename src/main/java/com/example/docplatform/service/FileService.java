package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.exception.TenantAccessDeniedException;
import com.example.docplatform.report.storage.DocumentStorageService;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileService {

    private final GeneratedDocumentRepository documentRepository;
    private final DocumentStorageService storageService;

    public String getDownloadUrl(Long tenantId, String documentId) throws Exception {
        GeneratedDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (!doc.getTenantId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied to document: " + documentId);
        }

        if (doc.getStatus() != ReportStatus.COMPLETED) {
            throw new IllegalStateException("Document not ready yet: " + doc.getStatus());
        }

        return storageService.generatePresignedUrl(doc.getMinioObjectKey());
    }
}
