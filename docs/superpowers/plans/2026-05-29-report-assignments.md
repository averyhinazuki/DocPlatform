# Report Assignments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow admins to assign a report task (template + guidance note) to a user; the user generates the report from their dashboard, and the assignment auto-completes on submit.

**Architecture:** New `report_assignments` MySQL table with MyBatis-Plus entity/mapper; a new `AssignmentService` handles CRUD and completion; `ReportController` calls `assignmentService.complete()` after queuing. Frontend adds a "My Assignments" card on Dashboard, an assignment-aware mode in ReportsView (locked template + admin note banner), and an Assignments panel in AdminView.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis-Plus, Spring Data MongoDB, Vue 3 + Pinia + Vue Router + Axios

---

## File Map

**Create (backend):**
- `src/main/resources/schema.sql` — add `report_assignments` table
- `src/main/java/com/example/docplatform/enums/AssignmentStatus.java` — PENDING/COMPLETED
- `src/main/java/com/example/docplatform/entity/ReportAssignment.java` — MyBatis-Plus entity
- `src/main/java/com/example/docplatform/mapper/ReportAssignmentMapper.java` — extends BaseMapper
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentRequest.java` — create payload
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentResponse.java` — admin list view
- `src/main/java/com/example/docplatform/dto/assignment/MyAssignmentResponse.java` — user pending view
- `src/main/java/com/example/docplatform/service/AssignmentService.java` — business logic
- `src/main/java/com/example/docplatform/controller/AssignmentController.java` — REST endpoints

**Modify (backend):**
- `src/main/java/com/example/docplatform/dto/report/ReportRequest.java` — make scheduleId nullable, add assignmentId
- `src/main/java/com/example/docplatform/service/ReportService.java` — handle null scheduleId
- `src/main/java/com/example/docplatform/controller/ReportController.java` — inject AssignmentService, call complete after queue

**Modify (tests):**
- `src/test/java/com/example/docplatform/service/ReportServiceTest.java` — update constructor calls (7 args now)

**Create (test):**
- `src/test/java/com/example/docplatform/service/AssignmentServiceTest.java`

**Create (frontend):**
- `frontend/src/api/assignments.js`

**Modify (frontend):**
- `frontend/src/views/DashboardView.vue`
- `frontend/src/views/ReportsView.vue`
- `frontend/src/views/AdminView.vue`

---

### Task 1: Add `report_assignments` table to schema.sql

**Files:**
- Modify: `src/main/resources/schema.sql`

- [ ] **Step 1: Append the table DDL**

Open `src/main/resources/schema.sql` and append at the end:

```sql
CREATE TABLE IF NOT EXISTS report_assignments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT NOT NULL,
  created_by   BIGINT NOT NULL,
  assignee_id  BIGINT NOT NULL,
  template_id  VARCHAR(100) NOT NULL,
  notes        TEXT,
  status       ENUM('PENDING','COMPLETED') DEFAULT 'PENDING',
  document_id  VARCHAR(100),
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME
);
```

- [ ] **Step 2: Apply to running database**

In your MySQL shell (or DBeaver), run the statement above. If the application auto-runs schema.sql on start, just restart the Spring Boot app.

- [ ] **Step 3: Verify table exists**

```sql
SHOW TABLES LIKE 'report_assignments';
DESCRIBE report_assignments;
```

Expected: table listed, 10 columns visible.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/schema.sql
git commit -m "feat: add report_assignments table"
```

---

### Task 2: AssignmentStatus enum

**Files:**
- Create: `src/main/java/com/example/docplatform/enums/AssignmentStatus.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/example/docplatform/service/AssignmentServiceTest.java` with a placeholder:

```java
package com.example.docplatform.service;

import com.example.docplatform.enums.AssignmentStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AssignmentServiceTest {

    @Test
    void assignmentStatus_hasPendingAndCompleted() {
        assertThat(AssignmentStatus.values()).containsExactlyInAnyOrder(
            AssignmentStatus.PENDING, AssignmentStatus.COMPLETED);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```
./mvnw test -pl . -Dtest=AssignmentServiceTest#assignmentStatus_hasPendingAndCompleted -q
```

Expected: compilation failure — `AssignmentStatus` does not exist.

- [ ] **Step 3: Create the enum**

```java
package com.example.docplatform.enums;

public enum AssignmentStatus { PENDING, COMPLETED }
```

- [ ] **Step 4: Run to verify it passes**

```
./mvnw test -pl . -Dtest=AssignmentServiceTest#assignmentStatus_hasPendingAndCompleted -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/docplatform/enums/AssignmentStatus.java \
        src/test/java/com/example/docplatform/service/AssignmentServiceTest.java
git commit -m "feat: add AssignmentStatus enum"
```

---

### Task 3: ReportAssignment entity and mapper

**Files:**
- Create: `src/main/java/com/example/docplatform/entity/ReportAssignment.java`
- Create: `src/main/java/com/example/docplatform/mapper/ReportAssignmentMapper.java`

- [ ] **Step 1: Create the entity**

```java
package com.example.docplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.docplatform.enums.AssignmentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_assignments")
public class ReportAssignment {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long createdBy;
    private Long assigneeId;
    private String templateId;
    private String notes;
    private AssignmentStatus status;
    private String documentId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
```

- [ ] **Step 2: Create the mapper**

```java
package com.example.docplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.docplatform.entity.ReportAssignment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportAssignmentMapper extends BaseMapper<ReportAssignment> {}
```

- [ ] **Step 3: Compile check**

```
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/docplatform/entity/ReportAssignment.java \
        src/main/java/com/example/docplatform/mapper/ReportAssignmentMapper.java
git commit -m "feat: add ReportAssignment entity and mapper"
```

---

### Task 4: Assignment DTOs

**Files:**
- Create: `src/main/java/com/example/docplatform/dto/assignment/AssignmentRequest.java`
- Create: `src/main/java/com/example/docplatform/dto/assignment/AssignmentResponse.java`
- Create: `src/main/java/com/example/docplatform/dto/assignment/MyAssignmentResponse.java`

- [ ] **Step 1: Create AssignmentRequest**

```java
package com.example.docplatform.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
    @NotNull Long assigneeId,
    @NotBlank String templateId,
    String notes
) {}
```

- [ ] **Step 2: Create AssignmentResponse**

```java
package com.example.docplatform.dto.assignment;

import com.example.docplatform.enums.AssignmentStatus;

import java.time.LocalDateTime;

public record AssignmentResponse(
    Long id,
    String assigneeUsername,
    String templateName,
    String notes,
    AssignmentStatus status,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    String documentId
) {}
```

- [ ] **Step 3: Create MyAssignmentResponse**

```java
package com.example.docplatform.dto.assignment;

import java.time.LocalDateTime;

public record MyAssignmentResponse(
    Long id,
    String templateId,
    String templateName,
    String notes,
    LocalDateTime createdAt
) {}
```

- [ ] **Step 4: Compile check**

```
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/docplatform/dto/assignment/
git commit -m "feat: add assignment DTO records"
```

---

### Task 5: AssignmentService

**Files:**
- Create: `src/main/java/com/example/docplatform/service/AssignmentService.java`
- Modify: `src/test/java/com/example/docplatform/service/AssignmentServiceTest.java`

- [ ] **Step 1: Add service tests to AssignmentServiceTest.java**

Replace the entire file with:

```java
package com.example.docplatform.service;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.dto.assignment.AssignmentRequest;
import com.example.docplatform.dto.assignment.AssignmentResponse;
import com.example.docplatform.dto.assignment.MyAssignmentResponse;
import com.example.docplatform.entity.ReportAssignment;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.AssignmentStatus;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.ReportAssignmentMapper;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.repository.ReportTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock ReportAssignmentMapper assignmentMapper;
    @Mock UserMapper userMapper;
    @Mock ReportTemplateRepository templateRepository;
    @InjectMocks AssignmentService assignmentService;

    @Test
    void assignmentStatus_hasPendingAndCompleted() {
        assertThat(AssignmentStatus.values()).containsExactlyInAnyOrder(
            AssignmentStatus.PENDING, AssignmentStatus.COMPLETED);
    }

    @Test
    void create_insertsAndReturnsAssignment() {
        when(assignmentMapper.insert(any())).thenReturn(1);

        ReportAssignment result = assignmentService.create(
            1L, 10L, new AssignmentRequest(20L, "tmpl-1", "Use Q1 figures"));

        assertThat(result.getTenantId()).isEqualTo(1L);
        assertThat(result.getCreatedBy()).isEqualTo(10L);
        assertThat(result.getAssigneeId()).isEqualTo(20L);
        assertThat(result.getTemplateId()).isEqualTo("tmpl-1");
        assertThat(result.getNotes()).isEqualTo("Use Q1 figures");
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PENDING);
        verify(assignmentMapper).insert(any(ReportAssignment.class));
    }

    @Test
    void listMine_returnsPendingAssignmentsForUser() {
        ReportAssignment a = new ReportAssignment();
        a.setId(1L);
        a.setAssigneeId(20L);
        a.setTenantId(1L);
        a.setTemplateId("tmpl-1");
        a.setNotes("Use Q1");
        a.setStatus(AssignmentStatus.PENDING);
        a.setCreatedAt(LocalDateTime.now());

        when(assignmentMapper.selectList(any())).thenReturn(List.of(a));

        ReportTemplate tmpl = new ReportTemplate();
        tmpl.setId("tmpl-1");
        tmpl.setName("Sales Report");
        when(templateRepository.findAllById(any())).thenReturn(List.of(tmpl));

        List<MyAssignmentResponse> result = assignmentService.listMine(20L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).templateName()).isEqualTo("Sales Report");
        assertThat(result.get(0).notes()).isEqualTo("Use Q1");
    }

    @Test
    void complete_updatesStatusAndDocumentId() {
        ReportAssignment a = new ReportAssignment();
        a.setId(5L);
        a.setTenantId(1L);
        a.setStatus(AssignmentStatus.PENDING);

        when(assignmentMapper.selectById(5L)).thenReturn(a);
        when(assignmentMapper.updateById(any())).thenReturn(1);

        assignmentService.complete(5L, 1L, "doc-abc");

        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(a.getDocumentId()).isEqualTo("doc-abc");
        assertThat(a.getCompletedAt()).isNotNull();
        verify(assignmentMapper).updateById(a);
    }

    @Test
    void complete_throwsWhenAssignmentNotInTenant() {
        ReportAssignment a = new ReportAssignment();
        a.setId(5L);
        a.setTenantId(99L); // different tenant

        when(assignmentMapper.selectById(5L)).thenReturn(a);

        assertThatThrownBy(() -> assignmentService.complete(5L, 1L, "doc-abc"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./mvnw test -pl . -Dtest=AssignmentServiceTest -q
```

Expected: compilation failure — `AssignmentService` does not exist.

- [ ] **Step 3: Create AssignmentService**

```java
package com.example.docplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.dto.assignment.AssignmentRequest;
import com.example.docplatform.dto.assignment.AssignmentResponse;
import com.example.docplatform.dto.assignment.MyAssignmentResponse;
import com.example.docplatform.entity.ReportAssignment;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.AssignmentStatus;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.ReportAssignmentMapper;
import com.example.docplatform.mapper.UserMapper;
import com.example.docplatform.repository.ReportTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final ReportAssignmentMapper assignmentMapper;
    private final UserMapper userMapper;
    private final ReportTemplateRepository templateRepository;

    public ReportAssignment create(Long tenantId, Long createdBy, AssignmentRequest req) {
        ReportAssignment a = new ReportAssignment();
        a.setTenantId(tenantId);
        a.setCreatedBy(createdBy);
        a.setAssigneeId(req.assigneeId());
        a.setTemplateId(req.templateId());
        a.setNotes(req.notes());
        a.setStatus(AssignmentStatus.PENDING);
        a.setCreatedAt(LocalDateTime.now());
        assignmentMapper.insert(a);
        return a;
    }

    public List<AssignmentResponse> listByTenant(Long tenantId) {
        List<ReportAssignment> list = assignmentMapper.selectList(
            new LambdaQueryWrapper<ReportAssignment>()
                .eq(ReportAssignment::getTenantId, tenantId)
                .orderByDesc(ReportAssignment::getCreatedAt));
        if (list.isEmpty()) return List.of();

        Set<Long> userIds = list.stream().map(ReportAssignment::getAssigneeId).collect(Collectors.toSet());
        Map<Long, String> usernameById = userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

        Set<String> tIds = list.stream().map(ReportAssignment::getTemplateId).collect(Collectors.toSet());
        Map<String, String> templateNameById = new HashMap<>();
        templateRepository.findAllById(tIds).forEach(t -> templateNameById.put(t.getId(), t.getName()));

        return list.stream().map(a -> new AssignmentResponse(
            a.getId(),
            usernameById.getOrDefault(a.getAssigneeId(), "unknown"),
            templateNameById.getOrDefault(a.getTemplateId(), "unknown"),
            a.getNotes(),
            a.getStatus(),
            a.getCreatedAt(),
            a.getCompletedAt(),
            a.getDocumentId()
        )).toList();
    }

    public List<MyAssignmentResponse> listMine(Long assigneeId, Long tenantId) {
        List<ReportAssignment> list = assignmentMapper.selectList(
            new LambdaQueryWrapper<ReportAssignment>()
                .eq(ReportAssignment::getAssigneeId, assigneeId)
                .eq(ReportAssignment::getTenantId, tenantId)
                .eq(ReportAssignment::getStatus, AssignmentStatus.PENDING)
                .orderByDesc(ReportAssignment::getCreatedAt));
        if (list.isEmpty()) return List.of();

        Set<String> tIds = list.stream().map(ReportAssignment::getTemplateId).collect(Collectors.toSet());
        Map<String, String> templateNameById = new HashMap<>();
        templateRepository.findAllById(tIds).forEach(t -> templateNameById.put(t.getId(), t.getName()));

        return list.stream().map(a -> new MyAssignmentResponse(
            a.getId(),
            a.getTemplateId(),
            templateNameById.getOrDefault(a.getTemplateId(), "unknown"),
            a.getNotes(),
            a.getCreatedAt()
        )).toList();
    }

    public void complete(Long assignmentId, Long tenantId, String documentId) {
        ReportAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || !a.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Assignment " + assignmentId + " not found");
        }
        a.setStatus(AssignmentStatus.COMPLETED);
        a.setDocumentId(documentId);
        a.setCompletedAt(LocalDateTime.now());
        assignmentMapper.updateById(a);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./mvnw test -pl . -Dtest=AssignmentServiceTest -q
```

Expected: BUILD SUCCESS, 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/docplatform/service/AssignmentService.java \
        src/test/java/com/example/docplatform/service/AssignmentServiceTest.java
git commit -m "feat: add AssignmentService with create, list, listMine, complete"
```

---

### Task 6: AssignmentController

**Files:**
- Create: `src/main/java/com/example/docplatform/controller/AssignmentController.java`

- [ ] **Step 1: Create the controller**

```java
package com.example.docplatform.controller;

import com.example.docplatform.dto.assignment.AssignmentRequest;
import com.example.docplatform.dto.assignment.AssignmentResponse;
import com.example.docplatform.dto.assignment.MyAssignmentResponse;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> create(
            @Valid @RequestBody AssignmentRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        assignmentService.create(user.tenantId(), user.userId(), req);
        return ResponseEntity.status(201).build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AssignmentResponse> listAll(@AuthenticationPrincipal TenantUserDetails user) {
        return assignmentService.listByTenant(user.tenantId());
    }

    @GetMapping("/mine")
    public List<MyAssignmentResponse> listMine(@AuthenticationPrincipal TenantUserDetails user) {
        return assignmentService.listMine(user.userId(), user.tenantId());
    }
}
```

- [ ] **Step 2: Compile check**

```
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Start the application and smoke-test the endpoints**

Start the app, then:

```bash
# As admin — create assignment
curl -b cookies.txt -X POST http://localhost:8080/api/assignments \
  -H "Content-Type: application/json" \
  -d '{"assigneeId":2,"templateId":"<paste-a-real-template-id>","notes":"Use Q1 2026 figures"}'
# Expected: 201 Created

# As admin — list all
curl -b cookies.txt http://localhost:8080/api/assignments
# Expected: JSON array with the assignment

# As the assigned user — list mine
curl -b user-cookies.txt http://localhost:8080/api/assignments/mine
# Expected: JSON array with PENDING assignments for that user
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/docplatform/controller/AssignmentController.java
git commit -m "feat: add AssignmentController (POST/GET /api/assignments)"
```

---

### Task 7: Modify ReportRequest and ReportService for nullable scheduleId + assignmentId

**Files:**
- Modify: `src/main/java/com/example/docplatform/dto/report/ReportRequest.java`
- Modify: `src/main/java/com/example/docplatform/service/ReportService.java`
- Modify: `src/test/java/com/example/docplatform/service/ReportServiceTest.java`

- [ ] **Step 1: Update ReportRequest**

Replace the full file content of `src/main/java/com/example/docplatform/dto/report/ReportRequest.java`:

```java
package com.example.docplatform.dto.report;

import com.example.docplatform.enums.FileFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record ReportRequest(
    Long scheduleId,           // nullable for assignment-based reports
    @NotBlank String reportType,
    @NotNull FileFormat format,
    @NotBlank String templateId,
    Map<String, Object> params,
    List<String> recipients,
    Long assignmentId          // nullable; triggers auto-complete when present
) {}
```

- [ ] **Step 2: Update ReportService to handle null scheduleId**

Replace the full file content of `src/main/java/com/example/docplatform/service/ReportService.java`:

```java
package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.kafka.event.ReportRequestedEvent;
import com.example.docplatform.kafka.producer.ReportJobProducer;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final RedissonClient redissonClient;
    private final ReportJobProducer producer;
    private final GeneratedDocumentRepository documentRepository;

    public String requestReport(Long tenantId, ReportRequest req) {
        String lockKey = req.scheduleId() != null
            ? "report:" + tenantId + ":" + req.scheduleId()
            : "report:assignment:" + tenantId + ":" + req.assignmentId();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Report generation already in progress for this schedule");
            }

            if (req.scheduleId() != null) {
                boolean alreadyQueued = documentRepository.existsByScheduleIdAndStatusIn(
                    req.scheduleId(), List.of(ReportStatus.PENDING, ReportStatus.IN_PROGRESS));
                if (alreadyQueued) {
                    throw new IllegalStateException("Report already queued");
                }
            }

            GeneratedDocument doc = new GeneratedDocument();
            doc.setTenantId(tenantId);
            doc.setScheduleId(req.scheduleId());
            doc.setFileFormat(req.format());
            doc.setStatus(ReportStatus.PENDING);
            doc.setGeneratedAt(LocalDateTime.now());
            documentRepository.save(doc);

            producer.publishRequest(new ReportRequestedEvent(
                doc.getId(), tenantId, req.scheduleId(),
                req.reportType(), req.format().name(),
                req.templateId(), req.params(), req.recipients()
            ));

            return doc.getId();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted acquiring lock", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
```

- [ ] **Step 3: Fix ReportServiceTest constructor calls (7 args now)**

Replace the two `new ReportRequest(...)` calls in `src/test/java/com/example/docplatform/service/ReportServiceTest.java`:

Find:
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of("a@b.com"))
```
Replace with:
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of("a@b.com"), null)
```

Find (second occurrence in `requestReport_throwsWhenLockNotAcquired`):
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of())
```
Replace with:
```java
new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of(), null)
```

- [ ] **Step 4: Run the existing ReportService tests**

```
./mvnw test -pl . -Dtest=ReportServiceTest -q
```

Expected: BUILD SUCCESS, 2 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/docplatform/dto/report/ReportRequest.java \
        src/main/java/com/example/docplatform/service/ReportService.java \
        src/test/java/com/example/docplatform/service/ReportServiceTest.java
git commit -m "feat: make scheduleId nullable and add assignmentId to ReportRequest"
```

---

### Task 8: Wire AssignmentService into ReportController

**Files:**
- Modify: `src/main/java/com/example/docplatform/controller/ReportController.java`

- [ ] **Step 1: Update ReportController to call complete after queuing**

Replace the full file content of `src/main/java/com/example/docplatform/controller/ReportController.java`:

```java
package com.example.docplatform.controller;

import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AssignmentService assignmentService;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestBody @Valid ReportRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        String docId = reportService.requestReport(user.tenantId(), req);
        if (req.assignmentId() != null) {
            assignmentService.complete(req.assignmentId(), user.tenantId(), docId);
        }
        return ResponseEntity.accepted().body(Map.of("documentId", docId));
    }
}
```

- [ ] **Step 2: Run all backend tests**

```
./mvnw test -q
```

Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 3: Smoke-test the assignment completion flow**

```bash
# As the assigned user — generate report with assignmentId
curl -b user-cookies.txt -X POST http://localhost:8080/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{"reportType":"SALES","format":"PDF","templateId":"<tmpl-id>","params":{},"recipients":["admin"],"assignmentId":1}'
# Expected: 202 Accepted with documentId

# As admin — verify assignment is COMPLETED
curl -b cookies.txt http://localhost:8080/api/assignments
# Expected: status "COMPLETED", documentId filled in
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/docplatform/controller/ReportController.java
git commit -m "feat: mark assignment COMPLETED after report queued"
```

---

### Task 9: Frontend API module for assignments

**Files:**
- Create: `frontend/src/api/assignments.js`

- [ ] **Step 1: Create the API module**

```javascript
import api from './axios'

export const getMyAssignments = () => api.get('/assignments/mine')
export const listAssignments = () => api.get('/assignments')
export const createAssignment = (data) => api.post('/assignments', data)
```

- [ ] **Step 2: Verify the file exists and has no syntax errors**

```bash
node -e "require('./frontend/src/api/assignments.js')" 2>&1 || echo "check with vite build instead"
cd frontend && npx vite build --mode development 2>&1 | head -20
```

Expected: no import errors from the assignments module.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/assignments.js
git commit -m "feat: add frontend assignments API module"
```

---

### Task 10: DashboardView — My Assignments card

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`

- [ ] **Step 1: Replace DashboardView.vue**

Replace the entire file with:

```vue
<template>
  <div class="page">
    <h1>Dashboard</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>My Assignments</h2>
      <div v-if="assignmentsLoading" class="empty-state">Loading…</div>
      <div v-else-if="assignments.length === 0" class="empty-state">No pending assignments.</div>
      <div v-for="a in assignments" :key="a.id" class="assignment-row">
        <div class="assignment-info">
          <p class="assignment-title">{{ a.templateName }}</p>
          <p v-if="a.notes" class="assignment-note">{{ a.notes }}</p>
          <p class="timestamp">Assigned {{ formatDate(a.createdAt) }}</p>
        </div>
        <router-link
          :to="`/reports?assignmentId=${a.id}&templateId=${a.templateId}`"
          class="btn btn-sm">
          Generate Report
        </router-link>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <h2>Notifications</h2>
        <button class="btn btn-ghost btn-sm"
                @click="markAll"
                :disabled="!notifStore.unread.length">
          Mark all read
        </button>
      </div>
      <div v-if="notifLoading" class="empty-state">Loading…</div>
      <div v-else-if="notifStore.unread.length === 0" class="empty-state">
        No unread notifications
      </div>
      <div v-for="n in notifStore.unread" :key="n.id" class="notif-row">
        <span class="notif-dot"></span>
        <div>
          <p>{{ n.message }}</p>
          <p class="timestamp">{{ formatDate(n.createdAt) }}</p>
        </div>
      </div>
      <p class="error-msg" v-if="notifError">{{ notifError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '../stores/notifications'
import { getMyAssignments } from '../api/assignments'

const notifStore = useNotificationStore()
const notifLoading = ref(false)
const notifError = ref('')

const assignments = ref([])
const assignmentsLoading = ref(false)

onMounted(async () => {
  assignmentsLoading.value = true
  notifLoading.value = true
  try {
    const [assignRes] = await Promise.all([
      getMyAssignments(),
      notifStore.fetch()
    ])
    assignments.value = assignRes.data
  } catch {
    notifError.value = 'Failed to load data'
  } finally {
    assignmentsLoading.value = false
    notifLoading.value = false
  }
})

async function markAll() {
  try {
    await notifStore.markAllRead()
  } catch {
    notifError.value = 'Failed to mark as read'
  }
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.card-header h2 { margin: 0; }
.assignment-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 0; border-bottom: 1px solid var(--border);
}
.assignment-row:last-child { border-bottom: none; }
.assignment-info { flex: 1; margin-right: 16px; }
.assignment-title { font-weight: 600; margin-bottom: 2px; }
.assignment-note { font-size: 13px; color: var(--text-2); margin-bottom: 2px; }
.notif-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--border); }
.notif-row:last-child { border-bottom: none; }
.notif-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); margin-top: 5px; flex-shrink: 0; }
.timestamp { font-size: 12px; color: var(--text-2); margin-top: 2px; }
.btn-sm { padding: 6px 14px; font-size: 13px; white-space: nowrap; }
</style>
```

- [ ] **Step 2: Start the dev server and verify the page renders**

```bash
cd frontend && npm run dev
```

Navigate to `http://localhost:5173/dashboard` as a user. Confirm:
- "My Assignments" card appears above "Notifications"
- Empty state shows "No pending assignments." when there are none
- Once an admin creates an assignment, the card shows the template name, note, and "Generate Report" button

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "feat: add My Assignments card to Dashboard"
```

---

### Task 11: ReportsView — assignment-aware mode

**Files:**
- Modify: `frontend/src/views/ReportsView.vue`

- [ ] **Step 1: Replace ReportsView.vue**

Replace the entire file with:

```vue
<template>
  <div class="page">
    <h1>Generate Report</h1>
    <div class="card" style="max-width: 560px;">
      <h2>{{ assignmentMode ? 'Complete Assignment' : 'One-off Report' }}</h2>

      <div v-if="assignmentMode && assignmentNotes" class="note-banner">
        <strong>Admin note:</strong> {{ assignmentNotes }}
      </div>

      <form @submit.prevent="submit">
        <div class="form-row" v-if="!assignmentMode">
          <div class="form-group">
            <label>Schedule ID</label>
            <input v-model.number="form.scheduleId" type="number" placeholder="42" required />
          </div>
          <div class="form-group">
            <label>Format</label>
            <select v-model="form.format">
              <option value="PDF">PDF</option>
              <option value="EXCEL">Excel</option>
              <option value="CSV">CSV</option>
            </select>
          </div>
        </div>
        <div class="form-group" v-if="assignmentMode">
          <label>Format</label>
          <select v-model="form.format">
            <option value="PDF">PDF</option>
            <option value="EXCEL">Excel</option>
            <option value="CSV">CSV</option>
          </select>
        </div>
        <div class="form-group">
          <label>Template</label>
          <select v-model="selectedTemplateId" :disabled="assignmentMode" required>
            <option value="" disabled>Select a template…</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} ({{ t.type }})</option>
          </select>
          <span v-if="selectedTemplate" class="hint" style="margin-top:4px;display:block">
            Params: {{ selectedTemplate.variables?.join(', ') || 'none' }}
          </span>
        </div>
        <div class="form-group">
          <label>Recipients</label>
          <div class="user-picker">
            <label v-for="u in tenantUsers" :key="u.id" class="user-pick-item">
              <input type="checkbox" :value="u.username" v-model="selectedRecipients" />
              {{ u.username }}
            </label>
            <span v-if="tenantUsers.length === 0" class="hint">No users in this tenant.</span>
          </div>
        </div>
        <div class="form-group">
          <label>Params (JSON)</label>
          <textarea v-model="paramsRaw" placeholder='{"region": "US"}'></textarea>
        </div>
        <button class="btn" type="submit" :disabled="loading">
          {{ loading ? 'Submitting…' : 'Generate Report' }}
        </button>
        <p class="error-msg" v-if="error">{{ error }}</p>
      </form>

      <div v-if="documentId" class="result-box">
        <p class="result-label">Report queued. Document ID:</p>
        <code class="doc-id">{{ documentId }}</code>
        <p class="result-hint">
          You'll receive a notification when it's ready.
          Copy this ID and use it on the Files page to download.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { generateReport } from '../api/reports'
import { listUsers } from '../api/users'
import { listTemplates } from '../api/templates'
import { getMyAssignments } from '../api/assignments'

const route = useRoute()
const assignmentId = computed(() => route.query.assignmentId ? Number(route.query.assignmentId) : null)
const assignmentMode = computed(() => assignmentId.value != null)
const assignmentNotes = ref('')

const form = reactive({ scheduleId: null, reportType: '', format: 'PDF', templateId: '' })
const selectedRecipients = ref([])
const tenantUsers = ref([])
const templates = ref([])
const selectedTemplateId = ref('')
const selectedTemplate = computed(() => templates.value.find(t => t.id === selectedTemplateId.value) ?? null)
const paramsRaw = ref('')
const loading = ref(false)
const error = ref('')
const documentId = ref('')

watch(selectedTemplateId, id => {
  const t = templates.value.find(t => t.id === id)
  if (t) { form.templateId = t.id; form.reportType = t.type }
})

onMounted(async () => {
  try {
    const [usersRes, templatesRes] = await Promise.all([listUsers(), listTemplates()])
    tenantUsers.value = usersRes.data
    templates.value = templatesRes.data

    if (assignmentMode.value) {
      const queryTemplateId = route.query.templateId
      if (queryTemplateId) selectedTemplateId.value = queryTemplateId

      const mineRes = await getMyAssignments()
      const match = mineRes.data.find(a => a.id === assignmentId.value)
      if (match) assignmentNotes.value = match.notes ?? ''
    }
  } catch {
    // non-critical
  }
})

async function submit() {
  loading.value = true
  error.value = ''
  documentId.value = ''
  try {
    let params = {}
    if (paramsRaw.value.trim()) {
      try {
        params = JSON.parse(paramsRaw.value)
      } catch {
        error.value = 'Invalid JSON in Params field'
        loading.value = false
        return
      }
    }
    const payload = {
      ...form,
      params,
      recipients: selectedRecipients.value,
      ...(assignmentMode.value ? { assignmentId: assignmentId.value, scheduleId: null } : {})
    }
    const res = await generateReport(payload)
    documentId.value = res.data.documentId
  } catch (e) {
    error.value = e.response?.data?.message ?? e.message ?? 'Failed to submit report'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.note-banner {
  padding: 12px 14px; margin-bottom: 20px;
  background: #eff6ff; border: 1px solid #bfdbfe; border-radius: var(--radius-sm);
  font-size: 14px; color: #1e40af;
}
.user-picker { display: flex; flex-wrap: wrap; gap: 8px; padding: 10px; background: var(--bg); border: 1px solid var(--border); border-radius: var(--radius-sm); }
.user-pick-item { display: flex; align-items: center; gap: 6px; font-size: 13px; cursor: pointer; }
.hint { font-size: 13px; color: var(--text-2); }
.result-box {
  margin-top: 24px; padding: 16px;
  background: var(--bg); border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}
.result-label { font-size: 13px; color: var(--text-2); margin-bottom: 6px; }
.doc-id {
  display: block; font-size: 13px;
  background: white; padding: 8px 12px;
  border-radius: 6px; border: 1px solid var(--border);
  word-break: break-all; margin-bottom: 8px;
}
.result-hint { font-size: 12px; color: var(--text-2); line-height: 1.6; }
</style>
```

- [ ] **Step 2: Verify normal flow still works**

Navigate to `http://localhost:5173/reports` (without query params). Confirm:
- "One-off Report" heading
- Schedule ID and Format in the same row
- Template dropdown is editable
- No blue admin note banner

- [ ] **Step 3: Verify assignment flow**

Navigate to `http://localhost:5173/reports?assignmentId=1&templateId=<real-template-id>`. Confirm:
- "Complete Assignment" heading
- Blue "Admin note" banner shows the notes text
- Template dropdown is disabled and pre-selected
- Format selector is visible
- Schedule ID field is hidden

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/ReportsView.vue
git commit -m "feat: assignment-aware mode in ReportsView (locked template, admin note banner)"
```

---

### Task 12: AdminView — Assignments panel

**Files:**
- Modify: `frontend/src/views/AdminView.vue`

- [ ] **Step 1: Replace AdminView.vue**

Replace the entire file with:

```vue
<template>
  <div class="page">
    <h1>Admin</h1>

    <div class="card" style="margin-bottom: 24px;">
      <h2>Tenants</h2>
      <div v-if="tenantsLoading" class="empty-state">Loading…</div>
      <div v-else-if="tenants.length === 0 && !tenantsError" class="empty-state">No tenants found.</div>
      <table v-else-if="tenants.length > 0">
        <thead>
          <tr><th>ID</th><th>Name</th><th>Slug</th><th>Plan</th><th>Created</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in tenants" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ t.name }}</td>
            <td>{{ t.slug }}</td>
            <td>{{ t.plan ?? '—' }}</td>
            <td>{{ formatDate(t.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="tenantsError">{{ tenantsError }}</p>
    </div>

    <div class="card" style="max-width: 400px; margin-bottom: 24px;">
      <h2>Update User Role</h2>
      <form @submit.prevent="submitRole">
        <div class="form-group">
          <label>User ID</label>
          <input v-model.number="roleForm.userId" type="number" placeholder="2" required />
        </div>
        <div class="form-group">
          <label>Role</label>
          <select v-model="roleForm.role">
            <option value="ADMIN">ADMIN</option>
            <option value="USER">USER</option>
          </select>
        </div>
        <button class="btn" type="submit" :disabled="roleUpdating">
          {{ roleUpdating ? 'Updating…' : 'Update Role' }}
        </button>
        <p class="error-msg" v-if="roleError">{{ roleError }}</p>
        <p class="success-msg" v-if="roleSuccess">Role updated successfully.</p>
      </form>
    </div>

    <div class="card" style="margin-bottom: 24px;">
      <h2>Assign Report Task</h2>
      <form @submit.prevent="submitAssignment" style="max-width: 480px;">
        <div class="form-group">
          <label>Assignee</label>
          <select v-model.number="assignForm.assigneeId" required>
            <option value="" disabled>Select user…</option>
            <option v-for="u in tenantUsers" :key="u.id" :value="u.id">{{ u.username }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Template</label>
          <select v-model="assignForm.templateId" required>
            <option value="" disabled>Select template…</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} ({{ t.type }})</option>
          </select>
        </div>
        <div class="form-group">
          <label>Notes <span class="hint">(guidance for the user)</span></label>
          <textarea v-model="assignForm.notes" placeholder="Use Q1 2026 figures…" rows="3"></textarea>
        </div>
        <button class="btn" type="submit" :disabled="assigning">
          {{ assigning ? 'Assigning…' : 'Assign Task' }}
        </button>
        <p class="error-msg" v-if="assignError">{{ assignError }}</p>
        <p class="success-msg" v-if="assignSuccess">Assignment created.</p>
      </form>
    </div>

    <div class="card">
      <h2>All Assignments</h2>
      <div v-if="assignmentsLoading" class="empty-state">Loading…</div>
      <div v-else-if="assignments.length === 0 && !assignmentsError" class="empty-state">No assignments yet.</div>
      <table v-else-if="assignments.length > 0">
        <thead>
          <tr>
            <th>Assignee</th><th>Template</th><th>Notes</th>
            <th>Status</th><th>Created</th><th>Completed</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in assignments" :key="a.id">
            <td>{{ a.assigneeUsername }}</td>
            <td>{{ a.templateName }}</td>
            <td class="notes-cell">{{ a.notes || '—' }}</td>
            <td>
              <span :class="['status-badge', a.status === 'COMPLETED' ? 'badge-done' : 'badge-pending']">
                {{ a.status }}
              </span>
            </td>
            <td>{{ formatDate(a.createdAt) }}</td>
            <td>{{ a.completedAt ? formatDate(a.completedAt) : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="error-msg" v-if="assignmentsError">{{ assignmentsError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listTenants, updateRole } from '../api/admin'
import { listUsers } from '../api/users'
import { listTemplates } from '../api/templates'
import { createAssignment, listAssignments } from '../api/assignments'

// Tenants
const tenants = ref([])
const tenantsLoading = ref(false)
const tenantsError = ref('')

// Role update
const roleForm = reactive({ userId: null, role: 'USER' })
const roleUpdating = ref(false)
const roleError = ref('')
const roleSuccess = ref(false)

// Assign form
const tenantUsers = ref([])
const templates = ref([])
const assignForm = reactive({ assigneeId: '', templateId: '', notes: '' })
const assigning = ref(false)
const assignError = ref('')
const assignSuccess = ref(false)

// Assignments table
const assignments = ref([])
const assignmentsLoading = ref(false)
const assignmentsError = ref('')

onMounted(async () => {
  await Promise.all([loadTenants(), loadUsersAndTemplates(), loadAssignments()])
})

async function loadTenants() {
  tenantsLoading.value = true
  tenantsError.value = ''
  try {
    const res = await listTenants()
    tenants.value = res.data
  } catch (e) {
    tenantsError.value = e.response?.status === 403
      ? 'Access denied — Admin role required'
      : 'Failed to load tenants'
  } finally {
    tenantsLoading.value = false
  }
}

async function loadUsersAndTemplates() {
  try {
    const [usersRes, templatesRes] = await Promise.all([listUsers(), listTemplates()])
    tenantUsers.value = usersRes.data
    templates.value = templatesRes.data
  } catch {
    // non-critical for pickers
  }
}

async function loadAssignments() {
  assignmentsLoading.value = true
  assignmentsError.value = ''
  try {
    const res = await listAssignments()
    assignments.value = res.data
  } catch {
    assignmentsError.value = 'Failed to load assignments'
  } finally {
    assignmentsLoading.value = false
  }
}

async function submitRole() {
  roleUpdating.value = true
  roleError.value = ''
  roleSuccess.value = false
  try {
    await updateRole(roleForm.userId, roleForm.role)
    roleSuccess.value = true
  } catch (e) {
    roleError.value = e.response?.status === 403
      ? 'Access denied — Admin role required'
      : e.response?.data?.message ?? 'Failed to update role'
  } finally {
    roleUpdating.value = false
  }
}

async function submitAssignment() {
  assigning.value = true
  assignError.value = ''
  assignSuccess.value = false
  try {
    await createAssignment({
      assigneeId: assignForm.assigneeId,
      templateId: assignForm.templateId,
      notes: assignForm.notes || null
    })
    assignSuccess.value = true
    Object.assign(assignForm, { assigneeId: '', templateId: '', notes: '' })
    await loadAssignments()
  } catch (e) {
    assignError.value = e.response?.data?.message ?? 'Failed to create assignment'
  } finally {
    assigning.value = false
  }
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}
</script>

<style scoped>
.notes-cell { max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.status-badge { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
.badge-pending { background: #fef3c7; color: #92400e; }
.badge-done { background: #d1fae5; color: #065f46; }
.hint { font-size: 12px; color: var(--text-2); }
</style>
```

- [ ] **Step 2: Verify in the browser as admin**

Navigate to `http://localhost:5173/admin`. Confirm:
- "Assign Report Task" section shows user picker (populated from `GET /api/users`) and template picker
- Submitting the form creates an assignment (201) and reloads the table
- "All Assignments" table shows rows with status badge (yellow PENDING, green COMPLETED)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/AdminView.vue
git commit -m "feat: add Assignments panel to AdminView"
```

---

### Task 13: End-to-end smoke test

This task has no code changes — it verifies the full happy path manually.

- [ ] **Step 1: Confirm the full flow works end-to-end**

1. Log in as admin.
2. Go to Admin → "Assign Report Task". Pick a user, pick a template, add a note. Submit.
3. Verify the assignment appears in the "All Assignments" table with status PENDING.
4. Log in as the assigned user.
5. Go to Dashboard. Confirm the "My Assignments" card shows the assignment with the note.
6. Click "Generate Report".
7. Verify you land on `/reports?assignmentId=X&templateId=Y`:
   - Blue admin note banner visible
   - Template dropdown disabled and pre-selected
   - Schedule ID field hidden
8. Fill in format, select at least one recipient, click Generate.
9. Confirm 202 response with documentId.
10. Log back in as admin.
11. Go to Admin → "All Assignments". Confirm the assignment status changed to COMPLETED with a documentId.
12. Go to Dashboard as the assigned user. Confirm the assignment no longer appears (it's COMPLETED, filtered out).

- [ ] **Step 2: Update CHANGELOG**

Append to `docs/superpowers/plans/CHANGELOG.md`:

```
## 2026-05-29 — Report Assignments

**Feature:** Admin can assign a report task to a user (template + guidance note). User sees it on Dashboard, clicks Generate Report, fills in params, submits. Assignment auto-completes on submit. Admin can track status.

**Backend files created:**
- `src/main/resources/schema.sql` (modified — report_assignments table)
- `src/main/java/com/example/docplatform/enums/AssignmentStatus.java`
- `src/main/java/com/example/docplatform/entity/ReportAssignment.java`
- `src/main/java/com/example/docplatform/mapper/ReportAssignmentMapper.java`
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentRequest.java`
- `src/main/java/com/example/docplatform/dto/assignment/AssignmentResponse.java`
- `src/main/java/com/example/docplatform/dto/assignment/MyAssignmentResponse.java`
- `src/main/java/com/example/docplatform/service/AssignmentService.java`
- `src/main/java/com/example/docplatform/controller/AssignmentController.java`

**Backend files modified:**
- `src/main/java/com/example/docplatform/dto/report/ReportRequest.java` (scheduleId nullable, assignmentId added)
- `src/main/java/com/example/docplatform/service/ReportService.java` (null scheduleId handling)
- `src/main/java/com/example/docplatform/controller/ReportController.java` (calls assignmentService.complete)

**Frontend files created:**
- `frontend/src/api/assignments.js`

**Frontend files modified:**
- `frontend/src/views/DashboardView.vue` (My Assignments card)
- `frontend/src/views/ReportsView.vue` (assignment-aware mode)
- `frontend/src/views/AdminView.vue` (Assignments panel)

**Tests created:**
- `src/test/java/com/example/docplatform/service/AssignmentServiceTest.java`

**Tests modified:**
- `src/test/java/com/example/docplatform/service/ReportServiceTest.java` (updated ReportRequest constructor to 7 args)
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/CHANGELOG.md
git commit -m "docs: update CHANGELOG for report assignments feature"
```
