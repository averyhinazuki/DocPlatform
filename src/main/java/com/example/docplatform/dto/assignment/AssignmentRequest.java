package com.example.docplatform.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
    @NotNull Long assigneeId,
    @NotBlank String templateId,
    String notes
) {}
