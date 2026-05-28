package com.example.docplatform.dto.assignment;

import com.example.docplatform.enums.AssignmentStatus;

import java.time.LocalDateTime;

public record AssignmentResponse(
    Long id,
    String assigneeUsername,
    String templateName,
    String notes,
    AssignmentStatus status,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    String documentId
) {}
