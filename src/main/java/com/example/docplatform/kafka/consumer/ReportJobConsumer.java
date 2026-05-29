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
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReportJobConsumer {

    private final List<ReportGenerator> generators;
    private final ReportTemplateRepository templateRepository;
    private final DocumentStorageService storageService;
    private final GeneratedDocumentRepository documentRepository;
    private final ReportJobProducer producer;

    @KafkaListener(topics = "report.requested", groupId = "docplatform-group")
    public void consume(ReportRequestedEvent event) {
        GeneratedDocument doc = documentRepository.findById(event.documentId()).orElseThrow();
        doc.setStatus(ReportStatus.IN_PROGRESS);
        documentRepository.save(doc);

        try {
            ReportTemplate template = templateRepository.findById(event.templateId()).orElseThrow();

            ReportGenerator generator = generators.stream()
                .filter(g -> g.supportedFormat().name().equals(event.fileFormat()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No generator for: " + event.fileFormat()));

            byte[] content = generator.generate(template, event.params());

            String contentType = switch (FileFormat.valueOf(event.fileFormat())) {
                case PDF   -> "application/pdf";
                case EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case CSV   -> "text/csv";
            };

            String objectKey = storageService.upload(
                event.tenantId(), template.getName() + "." + event.fileFormat().toLowerCase(),
                content, contentType);

            doc.setStatus(ReportStatus.COMPLETED);
            doc.setMinioBucket("reports");
            doc.setMinioObjectKey(objectKey);
            doc.setNote(event.note());
            doc.setDeliveredAt(LocalDateTime.now());
            documentRepository.save(doc);

            producer.publishCompleted(new ReportCompletedEvent(
                doc.getId(), event.tenantId(), objectKey, "reports",
                event.fileFormat(), event.recipients(),
                template.getName(), event.triggeredBy(), event.note()));

        } catch (Exception e) {
            doc.setStatus(ReportStatus.FAILED);
            doc.setErrorMsg(e.getMessage());
            documentRepository.save(doc);
        }
    }
}
