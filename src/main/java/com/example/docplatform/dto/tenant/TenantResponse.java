package com.example.docplatform.dto.tenant;

import java.time.LocalDateTime;

public record TenantResponse(
    Long id,
    String name,
    String slug,
    String plan,
    LocalDateTime createdAt
) {}
