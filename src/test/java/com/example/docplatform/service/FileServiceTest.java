package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.dto.file.DocumentSummary;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.exception.TenantAccessDeniedException;
import com.example.docplatform.report.storage.DocumentStorageService;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;

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

    @Test
    void getDownloadUrl_throwsWhenTenantMismatch() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(99L);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileService.getDownloadUrl(1L, "doc-1"))
            .isInstanceOf(TenantAccessDeniedException.class);
    }

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
    void delete_skipsMinioWhenNoObjectKey() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-2"); doc.setTenantId(1L);
        doc.setMinioObjectKey(null);
        when(documentRepository.findById("doc-2")).thenReturn(Optional.of(doc));

        fileService.delete(1L, "doc-2");

        verify(storageService, never()).delete(Mockito.any());
        verify(documentRepository).deleteById("doc-2");
    }

    @Test
    void delete_throwsResourceNotFoundWhenMissing() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.delete(1L, "missing"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_throwsTenantAccessDeniedForWrongTenant() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(99L);
        doc.setMinioObjectKey("reports/99/file.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileService.delete(1L, "doc-1"))
            .isInstanceOf(TenantAccessDeniedException.class);

        verify(storageService, never()).delete(Mockito.any());
        verify(documentRepository, never()).deleteById(Mockito.any());
    }
}
