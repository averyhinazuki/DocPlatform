# Real-Time Notifications (SSE + Redis Pub/Sub) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 15-second NotificationBell polling with real-time server push by wiring the half-built Redisson RTopic pipeline to the browser via Server-Sent Events.

**Architecture:** `InAppNotificationService` publishes a JSON notification payload to a user-scoped Redisson topic (`notifications:{tenantId}:{userId}`). `SseController` subscribes an RTopic listener for the connected user and streams the payload via `SseEmitter`. `NotificationBell.vue` opens a native `EventSource` and drops the `setInterval` poll. The existing `GET /api/notifications` fetch is kept for initial page-load hydration.

**Tech Stack:** Java 21, Spring Boot 3, Redisson `RTopic` + `SseEmitter`, Jackson `ObjectMapper`, Vue 3 native `EventSource`.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `src/main/java/com/example/docplatform/notification/InAppNotificationService.java` | Fix topic key → user-scoped; publish JSON payload instead of plain string |
| Modify | `src/test/java/com/example/docplatform/notification/InAppNotificationServiceTest.java` | Update existing test; add topic-key assertion |
| Create | `src/main/java/com/example/docplatform/controller/SseController.java` | `GET /api/notifications/stream` — creates emitter, subscribes RTopic listener |
| Create | `src/test/java/com/example/docplatform/controller/SseControllerTest.java` | Unit tests for SSE endpoint |
| Modify | `frontend/src/stores/notifications.js` | Add `connect()` / `disconnect()` using native `EventSource` |
| Modify | `frontend/src/components/NotificationBell.vue` | Replace `setInterval` with `notifStore.connect()` |

---

## Task 1: Fix InAppNotificationService — user-scoped topic + JSON payload

**Files:**
- Modify: `src/main/java/com/example/docplatform/notification/InAppNotificationService.java`
- Modify: `src/test/java/com/example/docplatform/notification/InAppNotificationServiceTest.java`

The current code publishes a plain string to `notifications:{tenantId}` (tenant-scoped, wrong). After this task it will publish a JSON object to `notifications:{tenantId}:{userId}` (user-scoped, correct).

- [ ] **Step 1: Replace `InAppNotificationServiceTest.java` with the updated test**

The existing `send_persistsAndPublishes` test will fail after our change because it verifies `verify(rTopic).publish("Report ready")` (plain string, old key). Replace the whole file:

```java
package com.example.docplatform.notification;

import com.example.docplatform.entity.User;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Mock ObjectMapper objectMapper;
    @InjectMocks InAppNotificationService service;

    @Test
    void send_persistsNotificationAndUsesUserScopedTopic() throws Exception {
        User u = new User(); u.setId(5L); u.setTenantId(1L); u.setUsername("a@b.com");
        when(userMapper.selectOne(any())).thenReturn(u);
        when(redissonClient.getTopic(anyString())).thenReturn(rTopic);
        when(objectMapper.writeValueAsString(any())).thenReturn(
            "{\"id\":null,\"message\":\"Report ready\",\"note\":null,\"documentId\":null}");

        service.send(1L, List.of("a@b.com"), "Report ready", null, null);

        verify(notificationRepository).save(argThat(n ->
            n.getTenantId().equals(1L) && n.getUserId().equals(5L) && !n.isRead()));
        verify(redissonClient).getTopic("notifications:1:5");
        verify(rTopic).publish(argThat(s -> s.contains("\"message\"")));
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
mvn test -Dtest=InAppNotificationServiceTest -q
```

Expected: FAIL — `verify(redissonClient).getTopic("notifications:1:5")` is not satisfied (current code uses tenant-only key).

- [ ] **Step 3: Update `InAppNotificationService.java`**

Replace the full file content:

```java
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
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
mvn test -Dtest=InAppNotificationServiceTest -q
```

Expected: BUILD SUCCESS, 1 test passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/docplatform/notification/InAppNotificationService.java \
        src/test/java/com/example/docplatform/notification/InAppNotificationServiceTest.java
git commit -m "fix: user-scoped RTopic key and JSON payload in InAppNotificationService"
```

---

## Task 2: Create SseController

**Files:**
- Create: `src/main/java/com/example/docplatform/controller/SseController.java`
- Create: `src/test/java/com/example/docplatform/controller/SseControllerTest.java`

`GET /api/notifications/stream` — requires authentication (session cookie sent automatically by `EventSource`). Spring Boot's async support handles `SseEmitter` with no extra configuration needed.

- [ ] **Step 1: Write `SseControllerTest.java`**

```java
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
            .doesNotThrow();
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
mvn test -Dtest=SseControllerTest -q
```

Expected: FAIL — `SseController` does not exist yet.

- [ ] **Step 3: Create `SseController.java`**

```java
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
```

- [ ] **Step 4: Run the tests to confirm they pass**

```bash
mvn test -Dtest=SseControllerTest -q
```

Expected: BUILD SUCCESS, 2 tests passing.

- [ ] **Step 5: Run full test suite to confirm no regressions**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/docplatform/controller/SseController.java \
        src/test/java/com/example/docplatform/controller/SseControllerTest.java
git commit -m "feat: SSE endpoint subscribes RTopic and streams notifications to browser"
```

---

## Task 3: Update frontend — EventSource replaces setInterval

**Files:**
- Modify: `frontend/src/stores/notifications.js`
- Modify: `frontend/src/components/NotificationBell.vue`

No automated test framework is configured for the frontend. Verification is manual — described in Task 4.

- [ ] **Step 1: Replace `frontend/src/stores/notifications.js`**

```js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUnread, markAllRead as apiMarkAllRead } from '../api/notifications'

export const useNotificationStore = defineStore('notifications', () => {
  const unread = ref([])
  const badgeCount = computed(() => unread.value.length)
  let eventSource = null

  async function fetch() {
    const res = await getUnread()
    unread.value = res.data
  }

  function connect() {
    if (eventSource) return
    eventSource = new EventSource('/api/notifications/stream')
    eventSource.onmessage = (e) => {
      const notif = JSON.parse(e.data)
      unread.value = [notif, ...unread.value]
    }
    eventSource.onerror = () => {
      fetch().catch(() => {})
    }
  }

  function disconnect() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  async function markAllRead() {
    await apiMarkAllRead()
    unread.value = []
  }

  return { unread, badgeCount, fetch, connect, disconnect, markAllRead }
})
```

- [ ] **Step 2: Update `frontend/src/components/NotificationBell.vue` — replace setInterval with connect()**

In the `<script setup>` block, replace:

```js
// OLD — remove these lines
let pollInterval = null

onMounted(() => {
  notifStore.fetch().catch(() => {})
  pollInterval = setInterval(() => notifStore.fetch().catch(() => {}), 15000)
})

onUnmounted(() => clearInterval(pollInterval))
```

With:

```js
// NEW
onMounted(() => {
  notifStore.fetch().catch(() => {})
  notifStore.connect()
})

onUnmounted(() => notifStore.disconnect())
```

The full updated `<script setup>` block:

```js
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '../stores/notifications'

const notifStore = useNotificationStore()
const router = useRouter()
const open = ref(false)

onMounted(() => {
  notifStore.fetch().catch(() => {})
  notifStore.connect()
})

onUnmounted(() => notifStore.disconnect())

function toggle() { open.value = !open.value }

function navigate(documentId) {
  open.value = false
  router.push('/files?docId=' + documentId)
}
</script>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/stores/notifications.js \
        frontend/src/components/NotificationBell.vue
git commit -m "feat: replace notification polling with EventSource SSE stream"
```

---

## Task 4: Manual verification

No automated frontend tests. Verify the end-to-end flow works in the browser.

- [ ] **Step 1: Start the application** (backend + frontend dev server)

- [ ] **Step 2: Open browser devtools → Network tab, filter by `stream`**

Navigate to any page. Confirm `GET /api/notifications/stream` shows as a pending EventStream request (status 200, type `eventsource`).

- [ ] **Step 3: Generate a report in another tab**

Submit a report from the Reports page. Within ~1 second of the report completing, confirm:
- The notification bell badge increments
- The notification appears in the dropdown immediately — without waiting 15 seconds

- [ ] **Step 4: Update CHANGELOG**

Add an entry to `docs/superpowers/plans/CHANGELOG.md` for this feature.
