package com.example.docplatform.kafka.event;

import java.util.List;

public record ReportCompletedEvent(
    String documentId,
    Long tenantId,
    String minioObjectKey,
    String minioBucket,
    String fileFormat,
    List<String> recipients,
    String templateName,
    String triggeredBy,
    String note
) {}
