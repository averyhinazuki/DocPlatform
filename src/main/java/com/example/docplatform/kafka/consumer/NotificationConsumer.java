package com.example.docplatform.kafka.consumer;

import com.example.docplatform.kafka.event.ReportCompletedEvent;
import com.example.docplatform.notification.EmailNotificationService;
import com.example.docplatform.notification.InAppNotificationService;
import com.example.docplatform.report.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final EmailNotificationService emailService;
    private final InAppNotificationService inAppService;
    private final DocumentStorageService storageService;

    @KafkaListener(topics = "report.completed", groupId = "docplatform-group")
    public void consume(ReportCompletedEvent event) {
        try {
            String downloadUrl = storageService.generatePresignedUrl(event.minioObjectKey());
            emailService.sendReportReady(event.recipients(), event.documentId(),
                event.fileFormat(), downloadUrl);
        } catch (Exception e) {
            log.warn("Email notification failed for document {} — in-app notification will still be sent: {}",
                event.documentId(), e.getMessage());
        }

        String msg = "Report '" + event.templateName() + "' is ready from " + event.triggeredBy();
        inAppService.send(event.tenantId(), event.recipients(), msg, event.documentId());
    }
}
