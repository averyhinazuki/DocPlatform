package com.example.docplatform.controller;

import com.example.docplatform.enums.Role;
import com.example.docplatform.security.TenantUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseControllerTest {

    @Mock RedissonClient redissonClient;
    @Mock RTopic topic;
    @InjectMocks SseController controller;

    private TenantUserDetails user() {
        return new TenantUserDetails(5L, 1L, "alice", "pw", Role.USER);
    }

    @Test
    void stream_registersListenerOnUserScopedTopic() {
        when(redissonClient.getTopic("notifications:1:5")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any())).thenReturn(42);

        SseEmitter emitter = controller.stream(user());

        assertThat(emitter).isNotNull();
        verify(redissonClient).getTopic("notifications:1:5");
        verify(topic).addListener(eq(String.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stream_listenerForwardsMessageWithoutThrowing() {
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        ArgumentCaptor<MessageListener<String>> listenerCaptor =
            ArgumentCaptor.forClass(MessageListener.class);
        when(topic.addListener(eq(String.class), listenerCaptor.capture())).thenReturn(42);

        controller.stream(user());

        assertThatCode(() ->
            listenerCaptor.getValue().onMessage("notifications:1:5",
                "{\"id\":\"n1\",\"message\":\"Report ready\",\"note\":null,\"documentId\":null}"))
            .doesNotThrowAnyException();
    }
}
