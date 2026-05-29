package com.example.docplatform.dto.template;

import java.util.List;

public record TemplateDetailResponse(
    String id,
    String name,
    String type,
    List<String> variables,
    String thymeleafTemplate
) {}
