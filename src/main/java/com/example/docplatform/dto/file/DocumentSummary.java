package com.example.docplatform.dto.file;

import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;

import java.time.LocalDateTime;

public record DocumentSummary(
    String id,
    FileFormat fileFormat,
    ReportStatus status,
    LocalDateTime generatedAt,
    Long scheduleId,
    String note
) {}
