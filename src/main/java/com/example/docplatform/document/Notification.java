package com.example.docplatform.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
public class Notification {
    @Id private String id;
    private Long tenantId;
    private Long userId;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}
