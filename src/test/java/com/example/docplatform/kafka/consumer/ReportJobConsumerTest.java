package com.example.docplatform.kafka.consumer;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.kafka.event.ReportCompletedEvent;
import com.example.docplatform.kafka.event.ReportRequestedEvent;
import com.example.docplatform.kafka.producer.ReportJobProducer;
import com.example.docplatform.report.generator.PdfReportGenerator;
import com.example.docplatform.report.storage.DocumentStorageService;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import com.example.docplatform.repository.ReportTemplateRepository;
import com.example.docplatform.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportJobConsumerTest {

    @Mock PdfReportGenerator generator;
    @Mock ReportTemplateRepository templateRepository;
    @Mock DocumentStorageService storageService;
    @Mock GeneratedDocumentRepository documentRepository;
    @Mock ReportJobProducer producer;
    @Mock QuotaService quotaService;

    ReportJobConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReportJobConsumer(
            List.of(generator), templateRepository, storageService,
            documentRepository, producer, quotaService
        );
    }

    private ReportRequestedEvent event(String documentId, String templateId, Long tenantId) {
        return new ReportRequestedEvent(
            documentId, tenantId, null, "SALES", "PDF",
            templateId, Map.of(), List.of(), "alice", null, null
        );
    }

    // ── exactly-once skip paths (unchanged) ──────────────────────────────────

    @Test
    void skipPath_republishesCompletedEventWhenMinioKeyAlreadySet() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.IN_PROGRESS);
        doc.setMinioObjectKey("reports/1/existing.pdf");
        doc.setMinioBucket("reports");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        ReportTemplate template = new ReportTemplate();
        template.setName("Sales");
        when(templateRepository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(event("doc-1", "tmpl-1", 1L));

        verify(generator, never()).generate(any(), any());
        verify(producer).publishCompleted(
            argThat(e -> "reports/1/existing.pdf".equals(e.minioObjectKey())));
        verify(quotaService).release(1L);
    }

    @Test
    void skipPath_silentlyExitsWhenAlreadyCompleted() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setMinioObjectKey("reports/1/done.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        consumer.consume(event("doc-1", "tmpl-1", 1L));

        verify(generator, never()).generate(any(), any());
        verify(producer, never()).publishCompleted(any());
        verify(quotaService, never()).release(any());
    }

    @Test
    void normalPath_savesObjectKeyBeforeMarkingCompleted() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.PENDING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        ReportTemplate template = new ReportTemplate();
        template.setName("Sales");
        when(templateRepository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(generator.supportedFormat()).thenReturn(FileFormat.PDF);
        when(generator.generate(any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.upload(anyLong(), anyString(), any(), anyString()))
            .thenReturn("reports/1/new.pdf");

        List<ReportStatus> savedStatuses = new ArrayList<>();
        List<String> savedKeys = new ArrayList<>();
        when(documentRepository.save(any())).thenAnswer(inv -> {
            GeneratedDocument d = inv.getArgument(0);
            savedStatuses.add(d.getStatus());
            savedKeys.add(d.getMinioObjectKey());
            return d;
        });

        consumer.consume(event("doc-1", "tmpl-1", 1L));

        // save[0] = IN_PROGRESS (no objectKey yet)
        // save[1] = Phase 1: objectKey persisted, status still IN_PROGRESS
        // save[2] = Phase 2: COMPLETED
        assertThat(savedStatuses.get(1)).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(savedKeys.get(1)).isEqualTo("reports/1/new.pdf");
        assertThat(savedStatuses.get(2)).isEqualTo(ReportStatus.COMPLETED);
        verify(producer).publishCompleted(any(ReportCompletedEvent.class));
        verify(quotaService).release(1L);
    }

    // ── non-retryable failures ────────────────────────────────────────────────

    @Test
    void nonRetryable_missingDocument_releasesQuotaAndReturns() throws Exception {
        when(documentRepository.findById("doc-missing")).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(
            () -> consumer.consume(event("doc-missing", "tmpl-1", 1L)));

        verify(documentRepository, never()).save(any());
        verify(quotaService).release(1L);
    }

    @Test
    void nonRetryable_unsupportedFormat_marksFailedWithoutRethrowing() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.PENDING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        ReportTemplate template = new ReportTemplate();
        template.setName("Sales");
        when(templateRepository.findById("tmpl-1")).thenReturn(Optional.of(template));
        // generator doesn't match "PDF" format → IllegalArgumentException thrown
        when(generator.supportedFormat()).thenReturn(FileFormat.CSV);

        List<ReportStatus> savedStatuses = new ArrayList<>();
        when(documentRepository.save(any())).thenAnswer(inv -> {
            savedStatuses.add(((GeneratedDocument) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        });

        assertThatNoException().isThrownBy(
            () -> consumer.consume(event("doc-1", "tmpl-1", 1L)));

        assertThat(savedStatuses).contains(ReportStatus.FAILED);
        verify(quotaService).release(1L);
    }

    // ── transient failure → re-throw for @RetryableTopic ─────────────────────

    @Test
    void transientFailure_rethrowsForRetry() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.PENDING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        ReportTemplate template = new ReportTemplate();
        template.setName("Sales");
        when(templateRepository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(generator.supportedFormat()).thenReturn(FileFormat.PDF);
        when(generator.generate(any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.upload(anyLong(), anyString(), any(), anyString()))
            .thenThrow(new RuntimeException("MinIO unavailable"));

        List<ReportStatus> savedStatuses = new ArrayList<>();
        when(documentRepository.save(any())).thenAnswer(inv -> {
            savedStatuses.add(((GeneratedDocument) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        });

        assertThatThrownBy(() -> consumer.consume(event("doc-1", "tmpl-1", 1L)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("MinIO unavailable");

        // Doc must NOT be marked FAILED — retry will pick it up via the normal path
        assertThat(savedStatuses).doesNotContain(ReportStatus.FAILED);
        // Quota slot stays HELD across retries — released only on a terminal outcome
        // (success, non-retryable failure, or DLT). Releasing here would let the tenant
        // exceed its concurrency limit while the job waits in a retry topic.
        verify(quotaService, never()).release(anyLong());
    }

    // ── DLT handler ───────────────────────────────────────────────────────────

    @Test
    void handleDlt_marksDocumentFailed() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setStatus(ReportStatus.IN_PROGRESS);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        List<ReportStatus> savedStatuses = new ArrayList<>();
        when(documentRepository.save(any())).thenAnswer(inv -> {
            savedStatuses.add(((GeneratedDocument) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        });

        consumer.handleDlt(event("doc-1", "tmpl-1", 1L), "report.requested-dlt");

        assertThat(savedStatuses).containsExactly(ReportStatus.FAILED);
        verify(documentRepository).save(any());
        // DLT is the terminal outcome for an exhausted job — the held quota slot is released here
        verify(quotaService).release(1L);
    }

    @Test
    void handleDlt_doesNotOverwriteCompletedDocument() {
        // Final attempt completed the report but failed publishing the completion event:
        // doc is COMPLETED, file exists in MinIO — DLT must not mark it FAILED.
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setStatus(ReportStatus.COMPLETED);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        consumer.handleDlt(event("doc-1", "tmpl-1", 1L), "report.requested-dlt");

        verify(documentRepository, never()).save(any());
        verify(quotaService).release(1L);
    }

    @Test
    void handleDlt_silentWhenDocumentNotFound() {
        when(documentRepository.findById("doc-gone")).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(
            () -> consumer.handleDlt(event("doc-gone", "tmpl-1", 1L), "report.requested-dlt"));

        verify(documentRepository, never()).save(any());
        verify(quotaService).release(1L);
    }
}
