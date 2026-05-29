package com.example.docplatform.kafka.event;

import java.util.List;
import java.util.Map;

public record ReportRequestedEvent(
    String documentId,
    Long tenantId,
    Long scheduleId,
    String reportType,
    String fileFormat,
    String templateId,
    Map<String, Object> params,
    List<String> recipients,
    String triggeredBy,
    String note
) {}
