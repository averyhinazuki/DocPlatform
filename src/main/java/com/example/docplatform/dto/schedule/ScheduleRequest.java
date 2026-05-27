package com.example.docplatform.dto.schedule;

import com.example.docplatform.enums.FileFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record ScheduleRequest(
    @NotBlank String name,
    @NotBlank String cronExpr,
    @NotBlank String reportType,
    @NotNull FileFormat format,
    @NotBlank String templateId,
    List<String> recipients,
    Map<String, Object> params
) {}
