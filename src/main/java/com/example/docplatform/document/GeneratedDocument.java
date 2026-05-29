package com.example.docplatform.document;

import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "generated_documents")
@Data
public class GeneratedDocument {
    @Id private String id;
    @Indexed private Long tenantId;
    @Indexed private Long scheduleId;
    private FileFormat fileFormat;
    private ReportStatus status;
    private String minioBucket;
    private String minioObjectKey;
    private LocalDateTime generatedAt;
    private LocalDateTime deliveredAt;
    private String errorMsg;
    private String note;
}
