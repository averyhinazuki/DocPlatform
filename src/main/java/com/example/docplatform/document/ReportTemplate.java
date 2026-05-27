package com.example.docplatform.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "report_templates")
@Data
public class ReportTemplate {
    @Id private String id;
    @Indexed private Long tenantId;
    private String name;
    private String type;
    private String thymeleafTemplate;
    private List<String> variables;
    private LocalDateTime createdAt;
}
