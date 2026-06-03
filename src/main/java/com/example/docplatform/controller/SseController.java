package com.example.docplatform.controller;

import com.example.docplatform.security.TenantUserDetails;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class SseController {

    private final RedissonClient redissonClient;

    @GetMapping("/stream")
    public SseEmitter stream(@AuthenticationPrincipal TenantUserDetails user) {
        SseEmitter emitter = new SseEmitter(300_000L);
        RTopic topic = redissonClient.getTopic(
            "notifications:" + user.tenantId() + ":" + user.userId());

        int listenerId = topic.addListener(String.class, (channel, payload) -> {
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        Runnable cleanup = () -> topic.removeListener(listenerId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        return emitter;
    }
}
