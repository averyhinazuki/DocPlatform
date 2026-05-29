package com.example.docplatform.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
public class Notification {
    @Id private String id;
    @Indexed private Long tenantId;
    @Indexed private Long userId;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
    private String documentId;
    private String note;
}
