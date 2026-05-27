package com.example.docplatform.dto.schedule;

import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ScheduleStatus;

import java.time.LocalDateTime;

public record ScheduleResponse(
    Long id,
    String name,
    String cronExpr,
    String reportType,
    FileFormat format,
    String templateId,
    ScheduleStatus status,
    LocalDateTime nextRunAt
) {}
