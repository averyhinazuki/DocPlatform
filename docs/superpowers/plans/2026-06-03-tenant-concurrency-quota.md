# Tenant Concurrency Quota Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cap concurrent report jobs per tenant with a Redis atomic counter; reject excess requests with HTTP 429.

**Architecture:** A `QuotaService` wraps all Redis counter operations (`acquire` / `release`). `ReportController` calls `acquire()` before publishing to Kafka and `release()` on publish failure. `ReportJobConsumer` calls `release()` in a `finally` block after every job. The per-tenant limit is stored in the `tenants` table (default 3) and read by `TenantService.getLimit()`.

**Tech Stack:** Redisson (`RAtomicLong`), MyBatis-Plus (`TenantMapper.selectById`), Spring `@ResponseStatus(TOO_MANY_REQUESTS)`

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `src/main/resources/schema.sql` | Modify | Add `concurrent_job_limit` column to `tenants` |
| `src/main/java/.../entity/Tenant.java` | Modify | Add `concurrentJobLimit` field |
| `src/main/java/.../service/TenantService.java` | Modify | Add `getLimit(tenantId)` |
| `src/main/java/.../exception/TenantQuotaExceededException.java` | Create | `@ResponseStatus(429)` exception |
| `src/main/java/.../service/QuotaService.java` | Create | Redis INCR/DECR + TTL safety valve |
| `src/main/java/.../controller/ReportController.java` | Modify | Acquire quota before Kafka; release on publish failure |
| `src/main/java/.../kafka/consumer/ReportJobConsumer.java` | Modify | Release quota in `finally` block |
| `src/test/java/.../service/TenantServiceTest.java` | Create | 2 tests for `getLimit()` |
| `src/test/java/.../service/QuotaServiceTest.java` | Create | 6 tests for acquire/release/TTL |
| `src/test/java/.../controller/ReportControllerTest.java` | Create | 2 tests for 429 propagation |
| `docs/superpowers/plans/CHANGELOG.md` | Modify | Record the feature |

---

## Task 1: Schema + Entity

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/java/com/example/docplatform/entity/Tenant.java`

- [ ] **Step 1: Add column to tenants DDL**

In `schema.sql`, update the `tenants` table to add `concurrent_job_limit`:

```sql
CREATE TABLE IF NOT EXISTS tenants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  plan VARCHAR(50) DEFAULT 'FREE',
  concurrent_job_limit INT NOT NULL DEFAULT 3,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: Add field to Tenant entity**

In `Tenant.java`, add after `private String plan;`:

```java
private Integer concurrentJobLimit;
```

- [ ] **Step 3: Commit**

```
git add src/main/resources/schema.sql src/main/java/com/example/docplatform/entity/Tenant.java
git commit -m "feat: add concurrent_job_limit to tenants table"
```

---

## Task 2: TenantService.getLimit()

**Files:**
- Modify: `src/main/java/com/example/docplatform/service/TenantService.java`
- Create: `src/test/java/com/example/docplatform/service/TenantServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/example/docplatform/service/TenantServiceTest.java`:

```java
package com.example.docplatform.service;

import com.example.docplatform.entity.Tenant;
import com.example.docplatform.mapper.TenantMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantMapper tenantMapper;
    @InjectMocks TenantService tenantService;

    @Test
    void getLimit_returnsConcurrentJobLimit() {
        Tenant t = new Tenant();
        t.setId(1L);
        t.setConcurrentJobLimit(5);
        when(tenantMapper.selectById(1L)).thenReturn(t);

        assertThat(tenantService.getLimit(1L)).isEqualTo(5);
    }

    @Test
    void getLimit_throwsWhenTenantNotFound() {
        when(tenantMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> tenantService.getLimit(99L))
            .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```
.\mvnw.cmd -Dtest=TenantServiceTest test -q
```

Expected: FAIL — `getLimit` method does not exist yet.

- [ ] **Step 3: Implement getLimit()**

Add to `TenantService.java` (after the `list()` method):

```java
public int getLimit(Long tenantId) {
    Tenant t = tenantMapper.selectById(tenantId);
    if (t == null) throw new IllegalStateException("Tenant not found: " + tenantId);
    return t.getConcurrentJobLimit();
}
```

- [ ] **Step 4: Run to verify it passes**

```
.\mvnw.cmd -Dtest=TenantServiceTest test -q
```

Expected: PASS — 2 tests passing.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/example/docplatform/service/TenantService.java src/test/java/com/example/docplatform/service/TenantServiceTest.java
git commit -m "feat: TenantService.getLimit() reads concurrent_job_limit from DB"
```

---

## Task 3: TenantQuotaExceededException + QuotaService

**Files:**
- Create: `src/main/java/com/example/docplatform/exception/TenantQuotaExceededException.java`
- Create: `src/main/java/com/example/docplatform/service/QuotaService.java`
- Create: `src/test/java/com/example/docplatform/service/QuotaServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/example/docplatform/service/QuotaServiceTest.java`:

```java
package com.example.docplatform.service;

import com.example.docplatform.exception.TenantQuotaExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock RedissonClient redissonClient;
    @Mock RAtomicLong counter;
    QuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new QuotaService(redissonClient);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);
    }

    @Test
    void acquire_allowsWhenUnderLimit() {
        when(counter.incrementAndGet()).thenReturn(2L);

        assertThatCode(() -> quotaService.acquire(1L, 3)).doesNotThrowAnyException();
    }

    @Test
    void acquire_allowsAtExactLimit() {
        when(counter.incrementAndGet()).thenReturn(3L);

        assertThatCode(() -> quotaService.acquire(1L, 3)).doesNotThrowAnyException();
    }

    @Test
    void acquire_throwsAndDecrementsWhenOverLimit() {
        when(counter.incrementAndGet()).thenReturn(4L);

        assertThatThrownBy(() -> quotaService.acquire(1L, 3))
            .isInstanceOf(TenantQuotaExceededException.class);
        verify(counter).decrementAndGet();
    }

    @Test
    void acquire_setsTtlOnFirstJob() {
        when(counter.incrementAndGet()).thenReturn(1L);

        quotaService.acquire(1L, 3);

        verify(counter).expire(3600L, TimeUnit.SECONDS);
    }

    @Test
    void acquire_doesNotResetTtlOnSubsequentJobs() {
        when(counter.incrementAndGet()).thenReturn(2L);

        quotaService.acquire(1L, 3);

        verify(counter, never()).expire(anyLong(), any());
    }

    @Test
    void release_decrementsCounter() {
        quotaService.release(1L);

        verify(redissonClient).getAtomicLong("tenant:1:running");
        verify(counter).decrementAndGet();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```
.\mvnw.cmd -Dtest=QuotaServiceTest test -q
```

Expected: FAIL — `QuotaService` and `TenantQuotaExceededException` do not exist.

- [ ] **Step 3: Create the exception class**

Create `src/main/java/com/example/docplatform/exception/TenantQuotaExceededException.java`:

```java
package com.example.docplatform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TenantQuotaExceededException extends RuntimeException {
    public TenantQuotaExceededException(Long tenantId, int limit) {
        super("Concurrent report limit (" + limit + ") reached for tenant "
              + tenantId + ". Upgrade your plan.");
    }
}
```

- [ ] **Step 4: Create QuotaService**

Create `src/main/java/com/example/docplatform/service/QuotaService.java`:

```java
package com.example.docplatform.service;

import com.example.docplatform.exception.TenantQuotaExceededException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final RedissonClient redissonClient;

    public void acquire(Long tenantId, int limit) {
        RAtomicLong counter = redissonClient.getAtomicLong("tenant:" + tenantId + ":running");
        long current = counter.incrementAndGet();
        if (current == 1) {
            counter.expire(3600L, TimeUnit.SECONDS);
        }
        if (current > limit) {
            counter.decrementAndGet();
            throw new TenantQuotaExceededException(tenantId, limit);
        }
    }

    public void release(Long tenantId) {
        redissonClient.getAtomicLong("tenant:" + tenantId + ":running").decrementAndGet();
    }
}
```

- [ ] **Step 5: Run to verify it passes**

```
.\mvnw.cmd -Dtest=QuotaServiceTest test -q
```

Expected: PASS — 6 tests passing.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/example/docplatform/exception/TenantQuotaExceededException.java src/main/java/com/example/docplatform/service/QuotaService.java src/test/java/com/example/docplatform/service/QuotaServiceTest.java
git commit -m "feat: QuotaService with Redis INCR/DECR and 3600s TTL safety valve"
```

---

## Task 4: Wire QuotaService into ReportController

**Files:**
- Modify: `src/main/java/com/example/docplatform/controller/ReportController.java`
- Create: `src/test/java/com/example/docplatform/controller/ReportControllerTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/example/docplatform/controller/ReportControllerTest.java`:

```java
package com.example.docplatform.controller;

import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.Role;
import com.example.docplatform.exception.TenantQuotaExceededException;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.AttachmentParserService;
import com.example.docplatform.service.QuotaService;
import com.example.docplatform.service.ReportService;
import com.example.docplatform.service.TenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock ReportService reportService;
    @Mock AssignmentService assignmentService;
    @Mock AttachmentParserService attachmentParser;
    @Mock QuotaService quotaService;
    @Mock TenantService tenantService;
    @InjectMocks ReportController controller;

    private TenantUserDetails user() {
        return new TenantUserDetails(1L, 2L, "alice", "pw", Role.USER);
    }

    private ReportRequest req() {
        return new ReportRequest(null, "sales", FileFormat.PDF,
                "tpl1", Map.of(), List.of(), null, null, null);
    }

    @Test
    void generate_acquiresQuotaWithTenantLimit() {
        when(tenantService.getLimit(2L)).thenReturn(3);
        when(reportService.requestReport(anyLong(), any(), any(), anyLong())).thenReturn("doc1");

        controller.generate(req(), null, user());

        verify(quotaService).acquire(2L, 3);
    }

    @Test
    void generate_propagates429WhenQuotaExceeded() {
        when(tenantService.getLimit(2L)).thenReturn(3);
        doThrow(new TenantQuotaExceededException(2L, 3)).when(quotaService).acquire(2L, 3);

        assertThatThrownBy(() -> controller.generate(req(), null, user()))
            .isInstanceOf(TenantQuotaExceededException.class);
        verify(reportService, never()).requestReport(anyLong(), any(), any(), anyLong());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```
.\mvnw.cmd -Dtest=ReportControllerTest test -q
```

Expected: FAIL — `QuotaService` and `TenantService` are not injected in `ReportController` yet.

- [ ] **Step 3: Update ReportController**

Replace the full contents of `ReportController.java`:

```java
package com.example.docplatform.controller;

import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.AttachmentParserService;
import com.example.docplatform.service.QuotaService;
import com.example.docplatform.service.ReportService;
import com.example.docplatform.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AssignmentService assignmentService;
    private final AttachmentParserService attachmentParser;
    private final QuotaService quotaService;
    private final TenantService tenantService;

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> generate(
            @RequestPart("request") @Valid ReportRequest req,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal TenantUserDetails user) {

        int limit = tenantService.getLimit(user.tenantId());
        quotaService.acquire(user.tenantId(), limit);

        try {
            ReportRequest effectiveReq = req;
            if (file != null && !file.isEmpty()) {
                Map<String, Object> fileParams = attachmentParser.parse(file);
                Map<String, Object> merged = new HashMap<>(fileParams);
                if (req.params() != null) {
                    req.params().forEach((k, v) -> {
                        if (v != null && !v.toString().isBlank()) merged.put(k, v);
                    });
                }
                effectiveReq = new ReportRequest(
                        req.scheduleId(), req.reportType(), req.format(),
                        req.templateId(), merged, req.recipients(), req.assignmentId(), req.note(), req.contentOverride()
                );
            }

            String docId = reportService.requestReport(user.tenantId(), effectiveReq, user.username(), user.userId());
            if (effectiveReq.assignmentId() != null) {
                assignmentService.complete(effectiveReq.assignmentId(), user.tenantId(), docId);
            }
            return ResponseEntity.accepted().body(Map.of("documentId", docId));
        } catch (Exception e) {
            quotaService.release(user.tenantId());
            throw e;
        }
    }
}
```

> The `catch` block releases the quota if the Kafka publish fails before the consumer ever runs, preventing a permanent slot leak.

- [ ] **Step 4: Run the controller tests**

```
.\mvnw.cmd -Dtest=ReportControllerTest test -q
```

Expected: PASS — 2 tests passing.

- [ ] **Step 5: Run the full test suite**

```
.\mvnw.cmd test -q
```

Expected: All existing tests pass alongside the new ones.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/example/docplatform/controller/ReportController.java src/test/java/com/example/docplatform/controller/ReportControllerTest.java
git commit -m "feat: quota check in ReportController before Kafka publish"
```

---

## Task 5: Release quota in ReportJobConsumer

**Files:**
- Modify: `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java`

- [ ] **Step 1: Add QuotaService dependency and finally block**

Replace the full contents of `ReportJobConsumer.java`:

```java
package com.example.docplatform.kafka.consumer;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.kafka.event.ReportCompletedEvent;
import com.example.docplatform.kafka.event.ReportRequestedEvent;
import com.example.docplatform.kafka.producer.ReportJobProducer;
import com.example.docplatform.report.generator.ReportGenerator;
import com.example.docplatform.report.storage.DocumentStorageService;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import com.example.docplatform.repository.ReportTemplateRepository;
import com.example.docplatform.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReportJobConsumer {

    private final List<ReportGenerator> generators;
    private final ReportTemplateRepository templateRepository;
    private final DocumentStorageService storageService;
    private final GeneratedDocumentRepository documentRepository;
    private final ReportJobProducer producer;
    private final QuotaService quotaService;

    @KafkaListener(topics = "report.requested", groupId = "docplatform-group")
    public void consume(ReportRequestedEvent event) {
        GeneratedDocument doc = documentRepository.findById(event.documentId()).orElseThrow();
        doc.setStatus(ReportStatus.IN_PROGRESS);
        documentRepository.save(doc);

        try {
            ReportTemplate template = templateRepository.findById(event.templateId()).orElseThrow();

            ReportGenerator generator = generators.stream()
                .filter(g -> g.supportedFormat().name().equals(event.fileFormat()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No generator for: " + event.fileFormat()));

            Map<String, Object> params = event.params() != null
                ? new HashMap<>(event.params())
                : new HashMap<>();
            if (event.contentOverride() != null && !event.contentOverride().isBlank()) {
                params.put("__content", event.contentOverride());
            }
            byte[] content = generator.generate(template, params);

            String contentType = switch (FileFormat.valueOf(event.fileFormat())) {
                case PDF   -> "application/pdf";
                case EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case CSV   -> "text/csv";
            };

            String objectKey = storageService.upload(
                event.tenantId(), template.getName() + "." + event.fileFormat().toLowerCase(),
                content, contentType);

            doc.setStatus(ReportStatus.COMPLETED);
            doc.setMinioBucket("reports");
            doc.setMinioObjectKey(objectKey);
            doc.setNote(event.note());
            doc.setDeliveredAt(LocalDateTime.now());
            documentRepository.save(doc);

            producer.publishCompleted(new ReportCompletedEvent(
                doc.getId(), event.tenantId(), objectKey, "reports",
                event.fileFormat(), event.recipients(),
                template.getName(), event.triggeredBy(), event.note()));

        } catch (Exception e) {
            doc.setStatus(ReportStatus.FAILED);
            doc.setErrorMsg(e.getMessage());
            documentRepository.save(doc);
        } finally {
            quotaService.release(event.tenantId());
        }
    }
}
```

- [ ] **Step 2: Run the full test suite**

```
.\mvnw.cmd test -q
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```
git add src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java
git commit -m "feat: release quota slot in ReportJobConsumer finally block"
```

---

## Task 6: Update CHANGELOG

**Files:**
- Modify: `docs/superpowers/plans/CHANGELOG.md`

- [ ] **Step 1: Add entry at the top of CHANGELOG.md** (after the `# DocPlatform Changelog` line)

```markdown
## 2026-06-03 — Tenant Concurrency Quota

**Feature:** Each tenant has a `concurrent_job_limit` (default 3) in the `tenants` table. When a user submits a report, `ReportController` reads the limit via `TenantService.getLimit()` then calls `QuotaService.acquire(tenantId, limit)`, which atomically increments a Redis counter (`tenant:{id}:running`). Requests beyond the limit get HTTP 429 ("Concurrent report limit reached. Upgrade your plan."). The counter is decremented in `ReportJobConsumer`'s `finally` block (success or failure), and also released in `ReportController`'s catch block if the Kafka publish itself fails. A 3600-second TTL is set when the counter first goes from 0→1 as a safety valve against consumer crashes.

**DB migration (run once):**
```sql
ALTER TABLE tenants ADD COLUMN concurrent_job_limit INT NOT NULL DEFAULT 3;
```

**Backend files created:**
- `src/main/java/com/example/docplatform/exception/TenantQuotaExceededException.java`
- `src/main/java/com/example/docplatform/service/QuotaService.java`

**Backend files modified:**
- `src/main/resources/schema.sql` — added `concurrent_job_limit` to `tenants`
- `src/main/java/com/example/docplatform/entity/Tenant.java` — added `concurrentJobLimit` field
- `src/main/java/com/example/docplatform/service/TenantService.java` — added `getLimit(tenantId)`
- `src/main/java/com/example/docplatform/controller/ReportController.java` — quota acquire before Kafka; release on publish failure
- `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java` — quota release in `finally`

**Tests created:**
- `src/test/java/com/example/docplatform/service/TenantServiceTest.java` — 2 tests
- `src/test/java/com/example/docplatform/service/QuotaServiceTest.java` — 6 tests
- `src/test/java/com/example/docplatform/controller/ReportControllerTest.java` — 2 tests
```

- [ ] **Step 2: Commit**

```
git add docs/superpowers/plans/CHANGELOG.md
git commit -m "docs: changelog for tenant concurrency quota"
```
