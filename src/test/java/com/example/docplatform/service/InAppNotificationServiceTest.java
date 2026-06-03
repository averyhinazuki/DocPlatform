package com.example.docplatform.service;

import com.example.docplatform.document.Notification;
import com.example.docplatform.entity.User;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.notification.InAppNotificationService;
import com.example.docplatform.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserMapper userMapper;
    @Mock RedissonClient redissonClient;
    @Mock ObjectMapper objectMapper;
    @InjectMocks InAppNotificationService inAppNotificationService;

    private void stubRedisson() throws Exception {
        RTopic mockTopic = mock(RTopic.class);
        when(redissonClient.getTopic(anyString())).thenReturn(mockTopic);
        when(mockTopic.publish(any())).thenReturn(0L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"message\":\"ok\"}");
    }

    private User stubUser() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectOne(any())).thenReturn(user);
        return user;
    }

    @Test
    void send_storesDocumentIdOnNotification() throws Exception {
        stubRedisson();
        stubUser();

        inAppNotificationService.send(10L, List.of("user@test.com"), "Report ready", "doc-123", null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDocumentId()).isEqualTo("doc-123");
    }

    @Test
    void send_allowsNullDocumentId() throws Exception {
        stubRedisson();
        stubUser();

        inAppNotificationService.send(10L, List.of("user@test.com"), "Hello", null, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDocumentId()).isNull();
    }
}
