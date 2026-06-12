package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.dto.PageResponse;
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

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

        when(documentRepository.findByTenantIdOrderByGeneratedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 20), 1));

        PageResponse<DocumentSummary> result = fileService.listByTenant(1L, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("doc-1");
        assertThat(result.items().get(0).fileFormat()).isEqualTo(FileFormat.PDF);
        assertThat(result.items().get(0).status()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(result.items().get(0).generatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 29, 10, 0));
        assertThat(result.items().get(0).scheduleId()).isEqualTo(42L);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void listByTenant_returnsEmptyPageWhenNoDocs() {
        when(documentRepository.findByTenantIdOrderByGeneratedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(fileService.listByTenant(1L, 0, 20).items()).isEmpty();
    }

    @Test
    void listByTenant_reportsTotalAcrossPages() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-2"); doc.setFileFormat(FileFormat.CSV);
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setGeneratedAt(LocalDateTime.of(2026, 6, 13, 8, 0));

        // page 1 of a 21-doc collection at size 10 → totalPages 3
        when(documentRepository.findByTenantIdOrderByGeneratedAtDesc(1L, PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(1, 10), 21));

        PageResponse<DocumentSummary> result = fileService.listByTenant(1L, 1, 10);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listByUser_returnsOnlyCallerDocs() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-u"); doc.setFileFormat(FileFormat.PDF);
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setGeneratedAt(LocalDateTime.of(2026, 5, 31, 9, 0));
        doc.setUserId(7L);

        when(documentRepository.findByTenantIdAndUserIdOrderByGeneratedAtDesc(1L, 7L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 20), 1));

        PageResponse<DocumentSummary> result = fileService.listByUser(1L, 7L, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("doc-u");
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
    void delete_adminRemovesFromStorageAndRepository() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(1L); doc.setUserId(5L);
        doc.setMinioObjectKey("reports/1/file.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        fileService.delete(1L, "doc-1", 99L, true);

        verify(storageService).delete("reports/1/file.pdf");
        verify(documentRepository).deleteById("doc-1");
    }

    @Test
    void delete_ownerCanDeleteOwnDoc() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-2"); doc.setTenantId(1L); doc.setUserId(7L);
        doc.setMinioObjectKey("reports/1/file.pdf");
        when(documentRepository.findById("doc-2")).thenReturn(Optional.of(doc));

        fileService.delete(1L, "doc-2", 7L, false);

        verify(storageService).delete("reports/1/file.pdf");
        verify(documentRepository).deleteById("doc-2");
    }

    @Test
    void delete_nonOwnerUserCannotDeleteOtherDoc() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-3"); doc.setTenantId(1L); doc.setUserId(5L);
        doc.setMinioObjectKey("reports/1/file.pdf");
        when(documentRepository.findById("doc-3")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileService.delete(1L, "doc-3", 7L, false))
            .isInstanceOf(TenantAccessDeniedException.class);

        verify(storageService, never()).delete(Mockito.any());
        verify(documentRepository, never()).deleteById(Mockito.any());
    }

    @Test
    void delete_skipsMinioWhenNoObjectKey() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-4"); doc.setTenantId(1L); doc.setUserId(7L);
        doc.setMinioObjectKey(null);
        when(documentRepository.findById("doc-4")).thenReturn(Optional.of(doc));

        fileService.delete(1L, "doc-4", 7L, false);

        verify(storageService, never()).delete(Mockito.any());
        verify(documentRepository).deleteById("doc-4");
    }

    @Test
    void delete_throwsResourceNotFoundWhenMissing() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.delete(1L, "missing", 7L, false))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_throwsTenantAccessDeniedForWrongTenant() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(99L); doc.setUserId(7L);
        doc.setMinioObjectKey("reports/99/file.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileService.delete(1L, "doc-1", 7L, true))
            .isInstanceOf(TenantAccessDeniedException.class);

        verify(storageService, never()).delete(Mockito.any());
        verify(documentRepository, never()).deleteById(Mockito.any());
    }
}
