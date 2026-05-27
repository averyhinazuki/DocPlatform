package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.exception.TenantAccessDeniedException;
import com.example.docplatform.report.storage.DocumentStorageService;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock GeneratedDocumentRepository documentRepository;
    @Mock DocumentStorageService storageService;
    @InjectMocks FileService fileService;

    @Test
    void getDownloadUrl_returnPresignedUrl() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(1L);
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setMinioObjectKey("reports/1/abc.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(storageService.generatePresignedUrl("reports/1/abc.pdf")).thenReturn("http://minio/signed");

        String url = fileService.getDownloadUrl(1L, "doc-1");

        assertThat(url).isEqualTo("http://minio/signed");
    }

    @Test
    void getDownloadUrl_throwsWhenTenantMismatch() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(99L);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileService.getDownloadUrl(1L, "doc-1"))
            .isInstanceOf(TenantAccessDeniedException.class);
    }
}
