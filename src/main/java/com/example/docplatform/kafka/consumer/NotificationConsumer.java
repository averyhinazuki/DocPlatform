package com.example.docplatform.kafka.consumer;

import com.example.docplatform.kafka.event.ReportCompletedEvent;
import com.example.docplatform.notification.EmailNotificationService;
import com.example.docplatform.notification.InAppNotificationService;
import com.example.docplatform.report.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
            String msg = "Report ready: " + event.documentId() + " (" + event.fileFormat() + ")";
            inAppService.send(event.tenantId(), event.recipients(), msg);
        } catch (Exception e) {
            throw new RuntimeException("Notification delivery failed", e);
        }
    }
}
