package com.example.docplatform.dto.tenant;

import jakarta.validation.constraints.NotBlank;

public record TenantRequest(
    @NotBlank String name,
    @NotBlank String slug,
    String plan
) {}
