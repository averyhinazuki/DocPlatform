package com.example.docplatform.controller;

import com.example.docplatform.document.Notification;
import com.example.docplatform.notification.InAppNotificationService;
import com.example.docplatform.security.TenantUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final InAppNotificationService notificationService;

    @GetMapping
    public List<Notification> getUnread(@AuthenticationPrincipal TenantUserDetails user) {
        return notificationService.getUnread(user.tenantId(), user.userId());
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal TenantUserDetails user) {
        notificationService.markAllRead(user.tenantId(), user.userId());
        return ResponseEntity.ok().build();
    }
}
