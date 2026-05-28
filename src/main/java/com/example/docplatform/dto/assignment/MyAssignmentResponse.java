package com.example.docplatform.dto.assignment;

import java.time.LocalDateTime;

public record MyAssignmentResponse(
    Long id,
    String templateId,
    String templateName,
    String notes,
    LocalDateTime createdAt
) {}
