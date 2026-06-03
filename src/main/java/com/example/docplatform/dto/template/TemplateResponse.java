package com.example.docplatform.dto.template;

import java.util.List;

public record TemplateResponse(
    String id,
    String name,
    String type,
    List<String> variables
) {}
