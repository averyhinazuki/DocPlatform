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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReportJobConsumer {

    private final List<ReportGenerator> generators;
    private final ReportTemplateRepository templateRepository;
    private final DocumentStorageService storageService;
    private final GeneratedDocumentRepository documentRepository;
    private final ReportJobProducer producer;
    private final QuotaService quotaService;

    @KafkaListener(topics = "report.requested", groupId = "docplatform-group")
    public void consume(ReportRequestedEvent event) {
        GeneratedDocument doc = documentRepository.findById(event.documentId()).orElseThrow(() -> new IllegalStateException("Document not found: " + event.documentId()));

        // Already fully processed — silent skip, quota already released in prior run
        if (doc.getStatus() == ReportStatus.COMPLETED) {
            return;
        }

        // MinIO upload succeeded in a prior attempt but COMPLETED save was lost (crash between phases)
        if (doc.getMinioObjectKey() != null) {
            try {
                ReportTemplate template = templateRepository.findById(event.templateId()).orElseThrow(() -> new IllegalStateException("Template not found: " + event.templateId()));
                completeAndPublish(doc, event, template.getName());
            } catch (Exception e) {
                doc.setStatus(ReportStatus.FAILED);
                doc.setErrorMsg(e.getMessage());
                documentRepository.save(doc);
            } finally {
                quotaService.release(event.tenantId());
            }
            return;
        }

        doc.setStatus(ReportStatus.IN_PROGRESS);
        documentRepository.save(doc);

        try {
            ReportTemplate template = templateRepository.findById(event.templateId()).orElseThrow(() -> new IllegalStateException("Template not found: " + event.templateId()));

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

        } catch (Exception e) {
            doc.setStatus(ReportStatus.FAILED);
            doc.setErrorMsg(e.getMessage());
            documentRepository.save(doc);
        } finally {
            quotaService.release(event.tenantId());
        }
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
