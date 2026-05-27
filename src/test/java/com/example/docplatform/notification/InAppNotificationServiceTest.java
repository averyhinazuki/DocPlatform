package com.example.docplatform.notification;

import com.example.docplatform.entity.User;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock RedissonClient redissonClient;
    @Mock RTopic rTopic;
    @Mock NotificationRepository notificationRepository;
    @Mock UserMapper userMapper;
    @InjectMocks InAppNotificationService service;

    @Test
    void send_persistsAndPublishes() {
        User u = new User(); u.setId(5L); u.setTenantId(1L); u.setUsername("a@b.com");
        when(userMapper.selectOne(any())).thenReturn(u);
        when(redissonClient.getTopic(anyString())).thenReturn(rTopic);

        service.send(1L, List.of("a@b.com"), "Report ready");

        verify(notificationRepository).save(argThat(n ->
            n.getTenantId().equals(1L) && n.getUserId().equals(5L) && !n.isRead()));
        verify(rTopic).publish("Report ready");
    }
}
