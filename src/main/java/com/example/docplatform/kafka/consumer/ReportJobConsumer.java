package com.example.docplatform.kafka.consumer;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.kafka.event.ReportCompletedEvent;
import com.example.docplatform.kafka.event.ReportRequestedEvent;
import com.example.docplatform.kafka.producer.ReportJobProducer;
import com.example.docplatform.report.generator.ReportGenerator;
import com.example.docplatform.report.storage.DocumentStorageService;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import com.example.docplatform.repository.ReportTemplateRepository;
import com.example.docplatform.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportJobConsumer {

    private final List<ReportGenerator> generators;
    private final ReportTemplateRepository templateRepository;
    private final DocumentStorageService storageService;
    private final GeneratedDocumentRepository documentRepository;
    private final ReportJobProducer producer;
    private final QuotaService quotaService;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 10_000, multiplier = 3.0), autoCreateTopics = "true")
    @KafkaListener(topics = "report.requested", groupId = "docplatform-group")
    public void consume(ReportRequestedEvent event) throws Exception {
        Optional<GeneratedDocument> docOpt = documentRepository.findById(event.documentId());
        if (docOpt.isEmpty()) {
            log.error("[Kafka][report-job] Document not found documentId={} — discarding", event.documentId());
            quotaService.release(event.tenantId());
            return;
        }
        GeneratedDocument doc = docOpt.get();

        // Already fully processed — silent skip, quota already released in prior run
        if (doc.getStatus() == ReportStatus.COMPLETED) {
            return;
        }

        // MinIO upload succeeded in a prior attempt but COMPLETED save was lost (crash between phases)
        // Quota is released only on terminal outcomes (success / non-retryable / DLT) — a rethrow keeps
        // the slot held so retries can't let the tenant exceed its concurrency limit.
        if (doc.getMinioObjectKey() != null) {
            try {
                ReportTemplate template = templateRepository.findById(event.templateId())
                    .orElseThrow(() -> new IllegalStateException("Template not found: " + event.templateId()));
                completeAndPublish(doc, event, template.getName());
                quotaService.release(event.tenantId());
            } catch (IllegalStateException | IllegalArgumentException e) {
                log.error("[Kafka][report-job] Non-retryable failure on skip path documentId={}: {}", event.documentId(), e.getMessage());
                markFailedAndRelease(doc, event.tenantId(), e.getMessage());
            } catch (Exception e) {
                log.error("[Kafka][report-job] Transient failure on skip path documentId={} — will retry: {}", event.documentId(), e.getMessage());
                throw e;
            }
            return;
        }

        doc.setStatus(ReportStatus.IN_PROGRESS);
        documentRepository.save(doc);

        try {
            ReportTemplate template = templateRepository.findById(event.templateId())
                .orElseThrow(() -> new IllegalStateException("Template not found: " + event.templateId()));

            ReportGenerator generator = generators.stream()
                .filter(g -> g.supportedFormat().name().equals(event.fileFormat()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No generator for: " + event.fileFormat()));

            Map<String, Object> params = event.params() != null
                ? new HashMap<>(event.params())
                : new HashMap<>();
            if (event.contentOverride() != null && !event.contentOverride().isBlank()) {
                params.put("__content", event.contentOverride());
            }
            byte[] content = generator.generate(template, params);

            String contentType = switch (FileFormat.valueOf(event.fileFormat())) {
                case PDF   -> "application/pdf";
                case EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case CSV   -> "text/csv";
            };

            String objectKey = storageService.upload(
                event.tenantId(), template.getName() + "." + event.fileFormat().toLowerCase(),
                content, contentType);

            // Phase 1: persist objectKey before marking COMPLETED (idempotency guard)
            doc.setMinioBucket("reports");
            doc.setMinioObjectKey(objectKey);
            documentRepository.save(doc);

            completeAndPublish(doc, event, template.getName());
            quotaService.release(event.tenantId());

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("[Kafka][report-job] Non-retryable failure documentId={}: {}", event.documentId(), e.getMessage());
            markFailedAndRelease(doc, event.tenantId(), e.getMessage());
        } catch (Exception e) {
            log.error("[Kafka][report-job] Transient failure documentId={} — will retry: {}", event.documentId(), e.getMessage());
            throw e;
        }
    }

    @DltHandler
    public void handleDlt(ReportRequestedEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("[Kafka][DLT] Report job exhausted retries topic={} documentId={}", topic, event.documentId());
        documentRepository.findById(event.documentId()).ifPresent(doc -> {
            // The final attempt may have completed the report and only failed on the
            // completion-event publish — never overwrite COMPLETED with FAILED.
            if (doc.getStatus() != ReportStatus.COMPLETED) {
                doc.setStatus(ReportStatus.FAILED);
                doc.setErrorMsg("Report job failed after max retries");
                documentRepository.save(doc);
            }
        });
        // Terminal outcome: the rethrowing attempts never released, so release exactly once here.
        // QuotaService's floor-at-zero guard absorbs a duplicate release if the DLT message is redelivered.
        quotaService.release(event.tenantId());
    }

    private void markFailedAndRelease(GeneratedDocument doc, Long tenantId, String errorMsg) {
        doc.setStatus(ReportStatus.FAILED);
        doc.setErrorMsg(errorMsg);
        documentRepository.save(doc);
        quotaService.release(tenantId);
    }

    private void completeAndPublish(GeneratedDocument doc, ReportRequestedEvent event, String templateName) {
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setNote(event.note());
        doc.setDeliveredAt(LocalDateTime.now());
        documentRepository.save(doc);

        producer.publishCompleted(new ReportCompletedEvent(
            doc.getId(), event.tenantId(), doc.getMinioObjectKey(), "reports",
            event.fileFormat(), event.recipients(),
            templateName, event.triggeredBy(), event.note()));
    }
}
