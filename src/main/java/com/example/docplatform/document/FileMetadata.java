package com.example.docplatform.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "file_metadata")
@Data
public class FileMetadata {
    @Id private String id;
    @Indexed private Long tenantId;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private String minioBucket;
    private String minioObjectKey;
    private LocalDateTime createdAt;
}
