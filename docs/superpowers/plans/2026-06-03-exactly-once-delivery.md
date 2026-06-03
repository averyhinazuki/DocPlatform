# Exactly-Once Report Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent duplicate report generation when a Kafka retry fires after MinIO upload but before the MongoDB COMPLETED save.

**Architecture:** Add two early-exit checks at the top of `ReportJobConsumer.consume()`. After `storageService.upload()`, persist `minioObjectKey` to MongoDB immediately (Phase 1 save, status stays `IN_PROGRESS`) before marking COMPLETED. On retry, if `doc.minioObjectKey != null`, skip regeneration and call `completeAndPublish()` directly. Extract `completeAndPublish()` as a private helper shared by both paths.

**Tech Stack:** Spring Kafka (at-least-once consumer), Spring Data MongoDB (`GeneratedDocumentRepository`), Mockito 5 (`@Mock`, `ArgumentCaptor`, `InOrder`)

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java` | Modify | Two early-exit checks; Phase 1 save; `completeAndPublish()` helper |
| `src/test/java/com/example/docplatform/kafka/consumer/ReportJobConsumerTest.java` | Create | 3 tests covering skip paths and normal-path save ordering |
| `docs/superpowers/plans/CHANGELOG.md` | Modify | Record the feature |

---

## Task 1: Write failing tests for the new consumer behaviour

**Files:**
- Create: `src/test/java/com/example/docplatform/kafka/consumer/ReportJobConsumerTest.java`

- [ ] **Step 1: Create the test file**

Create `src/test/java/com/example/docplatform/kafka/consumer/ReportJobConsumerTest.java`:

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportJobConsumerTest {

    @Mock ReportGenerator generator;
    @Mock ReportTemplateRepository templateRepository;
    @Mock DocumentStorageService storageService;
    @Mock GeneratedDocumentRepository documentRepository;
    @Mock ReportJobProducer producer;
    @Mock QuotaService quotaService;

    ReportJobConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReportJobConsumer(
            List.of(generator), templateRepository, storageService,
            documentRepository, producer, quotaService
        );
    }

    private ReportRequestedEvent event(String documentId, String templateId, Long tenantId) {
        return new ReportRequestedEvent(
            documentId, tenantId, null, "SALES", "PDF",
            templateId, Map.of(), List.of(), "alice", null, null
        );
    }

    @Test
    void skipPath_republishesCompletedEventWhenMinioKeyAlreadySet() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.IN_PROGRESS);
        doc.setMinioObjectKey("reports/1/existing.pdf");
        doc.setMinioBucket("reports");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        ReportTemplate template = new ReportTemplate();
        template.setName("Sales");
        when(templateRepository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(event("doc-1", "tmpl-1", 1L));

        verify(generator, never()).generate(any(), any());
        verify(producer).publishCompleted(
            argThat(e -> "reports/1/existing.pdf".equals(e.minioObjectKey())));
        verify(quotaService).release(1L);
    }

    @Test
    void skipPath_silentlyExitsWhenAlreadyCompleted() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setMinioObjectKey("reports/1/done.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        consumer.consume(event("doc-1", "tmpl-1", 1L));

        verify(generator, never()).generate(any(), any());
        verify(producer, never()).publishCompleted(any());
        verify(quotaService, never()).release(any());
    }

    @Test
    void normalPath_savesObjectKeyBeforeMarkingCompleted() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1");
        doc.setTenantId(1L);
        doc.setStatus(ReportStatus.PENDING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        ReportTemplate template = new ReportTemplate();
        template.setName("Sales");
        when(templateRepository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(generator.supportedFormat()).thenReturn(FileFormat.PDF);
        when(generator.generate(any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.upload(anyLong(), anyString(), any(), anyString()))
            .thenReturn("reports/1/new.pdf");

        List<ReportStatus> savedStatuses = new ArrayList<>();
        List<String> savedKeys = new ArrayList<>();
        when(documentRepository.save(any())).thenAnswer(inv -> {
            GeneratedDocument d = inv.getArgument(0);
            savedStatuses.add(d.getStatus());
            savedKeys.add(d.getMinioObjectKey());
            return d;
        });

        consumer.consume(event("doc-1", "tmpl-1", 1L));

        // save[0] = IN_PROGRESS (no objectKey yet)
        // save[1] = Phase 1: objectKey persisted, status still IN_PROGRESS
        // save[2] = Phase 2: COMPLETED
        assertThat(savedStatuses.get(1)).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(savedKeys.get(1)).isEqualTo("reports/1/new.pdf");
        assertThat(savedStatuses.get(2)).isEqualTo(ReportStatus.COMPLETED);
        verify(producer).publishCompleted(any(ReportCompletedEvent.class));
        verify(quotaService).release(1L);
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```
.\mvnw.cmd -Dtest=ReportJobConsumerTest test -q
```

Expected: 3 test failures.
- `skipPath_republishesCompletedEventWhenMinioKeyAlreadySet` — FAIL because current code calls `generator.generate()` instead of skipping
- `skipPath_silentlyExitsWhenAlreadyCompleted` — FAIL because current code calls `quotaService.release()` in finally
- `normalPath_savesObjectKeyBeforeMarkingCompleted` — FAIL because Phase 1 save doesn't exist yet (only 2 saves, not 3)

---

## Task 2: Refactor ReportJobConsumer

**Files:**
- Modify: `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java`

- [ ] **Step 1: Replace the full contents of ReportJobConsumer.java**

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

        // Already fully processed — silent skip, quota already released in prior run
        if (doc.getStatus() == ReportStatus.COMPLETED) {
            return;
        }

        // MinIO upload succeeded in a prior attempt but COMPLETED save was lost (crash between phases)
        if (doc.getMinioObjectKey() != null) {
            try {
                ReportTemplate template = templateRepository.findById(event.templateId()).orElseThrow();
                completeAndPublish(doc, event, template.getName());
            } finally {
                quotaService.release(event.tenantId());
            }
            return;
        }

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

            // Phase 1: persist objectKey before marking COMPLETED (idempotency guard)
            doc.setMinioBucket("reports");
            doc.setMinioObjectKey(objectKey);
            documentRepository.save(doc);

            completeAndPublish(doc, event, template.getName());

        } catch (Exception e) {
            doc.setStatus(ReportStatus.FAILED);
            doc.setErrorMsg(e.getMessage());
            documentRepository.save(doc);
        } finally {
            quotaService.release(event.tenantId());
        }
    }

    private void completeAndPublish(GeneratedDocument doc, ReportRequestedEvent event, String templateName) {
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setNote(event.note());
        doc.setDeliveredAt(LocalDateTime.now());
        documentRepository.save(doc);

        producer.publishCompleted(new ReportCompletedEvent(
            doc.getId(), event.tenantId(), doc.getMinioObjectKey(), "reports",
            event.fileFormat(), event.recipients(),
            templateName, event.triggeredBy(), event.note()));
    }
}
```

- [ ] **Step 2: Run new tests to verify they pass**

```
.\mvnw.cmd -Dtest=ReportJobConsumerTest test -q
```

Expected: PASS — 3 tests passing.

- [ ] **Step 3: Run the full test suite**

```
.\mvnw.cmd test -q
```

Expected: All existing tests pass alongside the new ones.

- [ ] **Step 4: Commit**

```
git add src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java src/test/java/com/example/docplatform/kafka/consumer/ReportJobConsumerTest.java
git commit -m "feat: exactly-once report delivery via two-phase MongoDB write"
```

---

## Task 3: Update CHANGELOG

**Files:**
- Modify: `docs/superpowers/plans/CHANGELOG.md`

- [ ] **Step 1: Add entry at the top of CHANGELOG.md** (after the `# DocPlatform Changelog` line)

```markdown
## 2026-06-03 — Exactly-Once Report Delivery

**Feature:** `ReportJobConsumer` now performs a two-phase MongoDB write to prevent duplicate report generation on Kafka retry. After `storageService.upload()` succeeds, `minioObjectKey` is immediately persisted to MongoDB while status stays `IN_PROGRESS` (Phase 1). The COMPLETED status and `ReportCompletedEvent` are then written in Phase 2. On any Kafka retry, the consumer checks `doc.minioObjectKey` first — if set, it skips regeneration entirely and calls `completeAndPublish()` with the existing key (re-publishes notification). If `doc.status == COMPLETED`, it exits silently with no quota release (quota was already released in the prior run's `finally` block).

**Backend files modified:**
- `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java` — two early-exit checks; Phase 1 save after MinIO upload; `completeAndPublish()` private helper extracted

**Tests created:**
- `src/test/java/com/example/docplatform/kafka/consumer/ReportJobConsumerTest.java` — 3 tests: skip path (minioObjectKey set), skip path (already COMPLETED), normal path save ordering
```

- [ ] **Step 2: Commit**

```
git add docs/superpowers/plans/CHANGELOG.md
git commit -m "docs: changelog for exactly-once report delivery"
```
