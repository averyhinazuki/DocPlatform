package com.example.docplatform.dto.report;

import com.example.docplatform.enums.FileFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record ReportRequest(
    Long scheduleId,
    @NotBlank String reportType,
    @NotNull FileFormat format,
    @NotBlank String templateId,
    Map<String, Object> params,
    List<String> recipients,
    Long assignmentId,
    String note,
    String contentOverride
) {}
