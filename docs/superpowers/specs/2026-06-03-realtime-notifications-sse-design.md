# Real-Time Notifications via SSE + Redis Pub/Sub — Design Spec

**Date:** 2026-06-03
**Status:** Approved

## Problem

The current notification system has two issues:

1. **Polling lag.** `NotificationBell.vue` polls `GET /api/notifications` every 15 seconds. A report that takes 10 seconds to generate makes the user wait up to 25 seconds total before seeing the notification (10s generation + up to 15s poll delay).

2. **Dead pub/sub + wrong scope.** `InAppNotificationService` already calls `redissonClient.getTopic("notifications:{tenantId}").publish(message)` — but nothing subscribes to it on the server side. Additionally, the topic key is tenant-scoped, meaning any future subscriber would push to all users in the tenant, not just the recipient.

## Solution

Complete the half-built Redis pub/sub pipeline and connect it to the browser via Server-Sent Events (SSE):

- Fix the Redisson topic key to be user-scoped: `notifications:{tenantId}:{userId}`
- Add a Spring SSE endpoint that subscribes to the user's topic and streams events to the browser
- Replace the `setInterval` poll in `NotificationBell.vue` with a native `EventSource` connection

SSE is chosen over WebSocket because communication is one-directional (server → browser only), requires no extra npm packages, and is supported natively by all modern browsers.

## Architecture

```
Report completes
  → Kafka: report.completed
    → NotificationConsumer → InAppNotificationService.send()
        → MongoDB: save Notification document
        → RTopic("notifications:{tenantId}:{userId}").publish(notifJson)
            → SseController listener fires (per connected user)
              → SseEmitter.send(notifJson) → browser EventSource
                → notificationStore.unread updated instantly
```

The REST endpoint `GET /api/notifications` and `POST /api/notifications/read-all` are kept unchanged. The initial page-load fetch still calls `GET /api/notifications` to hydrate the unread list. SSE only handles incremental pushes.

## Backend Changes

### 1. Fix topic key — `InAppNotificationService`

Change the RTopic publish from tenant-scoped to user-scoped:

```
// Before (wrong — pushes to all tenant users)
redissonClient.getTopic("notifications:" + tenantId).publish(message);

// After (correct — pushes only to the recipient)
redissonClient.getTopic("notifications:" + tenantId + ":" + userId).publish(notifJson);
```

The published payload changes from a plain `String` (the message) to a JSON object matching the `Notification` document fields: `{ id, message, note, documentId }`. This lets the frontend display the notification without a follow-up fetch.

### 2. New endpoint — `SseController`

`GET /api/notifications/stream` (requires authentication)

Behavior:
1. Create an `SseEmitter` with a 5-minute timeout.
2. Subscribe a Redisson `RTopic` listener on `notifications:{tenantId}:{userId}`.
3. On message received: call `emitter.send(payload)`.
4. On emitter completion, timeout, or error: unsubscribe the RTopic listener to prevent leaks.

The controller stores the `ListenerID` returned by Redisson so it can be deregistered cleanly.

### 3. No changes to `NotificationConsumer` or `NotificationController`

The Kafka consumer pipeline and REST endpoints are untouched.

## Frontend Changes

### 1. `stores/notifications.js`

Add a `connect()` method:
- Opens a native `EventSource` to `/api/notifications/stream`
- On `message`: parses the JSON payload and prepends it to `unread`
- On `error`: performs a single `fetch()` call to resync state (handles reconnect without polling loop)

### 2. `components/NotificationBell.vue`

- Replace `setInterval(() => notifStore.fetch(), 15000)` with `notifStore.connect()`
- Keep the initial `notifStore.fetch()` on mount (hydrates unread list on first load)
- `onUnmounted`: close the `EventSource`

## Error Handling

| Scenario | Behavior |
|---|---|
| SSE emitter times out (5-min default) | Browser `EventSource` reconnects automatically; new emitter + RTopic listener registered |
| User not connected (different page) | RTopic fires with no subscriber; event silently dropped; MongoDB persistence covers it |
| Consumer crash during generation | Exactly-once delivery (feature #2) ensures notification eventually fires on retry |
| `EventSource` error (network drop) | `onerror` handler calls `fetch()` once to resync; `EventSource` auto-reconnects |

## Data Contract

Payload published to RTopic and pushed via SSE:

```json
{
  "id": "...",
  "message": "Report 'Monthly Sales' is ready from alice",
  "note": "Q1 data only",
  "documentId": "..."
}
```

## Tests

**`SseControllerTest`**
- Emitter is created and RTopic listener registered on connect
- Incoming RTopic message is forwarded to emitter
- RTopic listener is deregistered on emitter completion

**`InAppNotificationServiceTest`**
- Topic key is user-scoped (`notifications:{tenantId}:{userId}`)
- Published payload is JSON containing message, note, documentId

## Files Touched

**Modified:**
- `src/main/java/com/example/docplatform/notification/InAppNotificationService.java`
- `frontend/src/stores/notifications.js`
- `frontend/src/components/NotificationBell.vue`

**Created:**
- `src/main/java/com/example/docplatform/controller/SseController.java`
- `src/test/java/com/example/docplatform/controller/SseControllerTest.java`
