package com.example.docplatform.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.document.Notification;
import com.example.docplatform.entity.User;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final RedissonClient redissonClient;
    private final NotificationRepository notificationRepository;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public void send(Long tenantId, List<String> recipientEmails, String message, String documentId, String note) {
        recipientEmails.forEach(email -> {
            User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, email));
            if (user == null) return;

            Notification n = new Notification();
            n.setTenantId(tenantId);
            n.setUserId(user.getId());
            n.setMessage(message);
            n.setRead(false);
            n.setCreatedAt(LocalDateTime.now());
            n.setDocumentId(documentId);
            n.setNote(note);
            notificationRepository.save(n);

            redissonClient.getTopic("notifications:" + tenantId + ":" + user.getId())
                .publish(toJson(n));
        });
    }

    public List<Notification> getUnread(Long tenantId, Long userId) {
        return notificationRepository.findByTenantIdAndUserIdAndReadFalse(tenantId, userId);
    }

    public void markAllRead(Long tenantId, Long userId) {
        notificationRepository.findByTenantIdAndUserIdAndReadFalse(tenantId, userId)
            .forEach(n -> { n.setRead(true); notificationRepository.save(n); });
    }

    private String toJson(Notification n) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("message", n.getMessage());
            m.put("note", n.getNote());
            m.put("documentId", n.getDocumentId());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{\"message\":\"notification\"}";
        }
    }
}
