package com.example.docplatform.repository;

import com.example.docplatform.document.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByTenantIdAndUserIdAndReadFalse(Long tenantId, Long userId);
}
