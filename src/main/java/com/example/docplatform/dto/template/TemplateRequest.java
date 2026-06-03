package com.example.docplatform.dto.template;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record TemplateRequest(
    @NotBlank String name,
    @NotBlank String type,
    @NotBlank String thymeleafTemplate,
    List<String> variables
) {}
