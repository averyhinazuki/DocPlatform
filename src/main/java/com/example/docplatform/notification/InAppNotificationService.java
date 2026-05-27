package com.example.docplatform.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.document.Notification;
import com.example.docplatform.entity.User;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final RedissonClient redissonClient;
    private final NotificationRepository notificationRepository;
    private final UserMapper userMapper;

    public void send(Long tenantId, List<String> recipientEmails, String message) {
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
            notificationRepository.save(n);

            // Real-time push — fire and forget
            redissonClient.getTopic("notifications:" + tenantId).publish(message);
        });
    }

    public List<Notification> getUnread(Long tenantId, Long userId) {
        return notificationRepository.findByTenantIdAndUserIdAndReadFalse(tenantId, userId);
    }

    public void markAllRead(Long tenantId, Long userId) {
        notificationRepository.findByTenantIdAndUserIdAndReadFalse(tenantId, userId)
            .forEach(n -> { n.setRead(true); notificationRepository.save(n); });
    }
}
