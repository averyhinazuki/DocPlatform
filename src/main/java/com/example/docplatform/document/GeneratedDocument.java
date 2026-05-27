package com.example.docplatform.document;

import com.example.docplatform.enums.ReportStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "generated_documents")
@Data
public class GeneratedDocument {
    @Id private String id;
    private Long tenantId;
    private Long scheduleId;
    private String fileFormat;
    private ReportStatus status;
    private String minioBucket;
    private String minioObjectKey;
    private LocalDateTime generatedAt;
    private LocalDateTime deliveredAt;
    private String errorMsg;
}
