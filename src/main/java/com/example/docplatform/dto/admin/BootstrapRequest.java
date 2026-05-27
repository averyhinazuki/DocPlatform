package com.example.docplatform.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record BootstrapRequest(
        @NotBlank String username,
        @NotBlank String password
) {}
