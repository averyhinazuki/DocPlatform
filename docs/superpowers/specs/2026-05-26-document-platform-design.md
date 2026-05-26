# Document Processing & Notification Platform — Design Spec
**Date:** 2026-05-26
**Status:** Approved

---

## 1. Project Overview

A multi-tenant enterprise platform that generates, schedules, and delivers reports (PDF, Excel, CSV) to users and tenants. Core differentiators: Kafka-driven async job queue, Redisson distributed locks preventing duplicate jobs, row-level multi-tenancy enforced automatically via MyBatis-Plus, and dual notification delivery (email + in-app via Redisson RTopic).

---

## 2. Tech Stack

| Layer | Tech |
|---|---|
| Framework | Spring Boot 3.x · Java 17 (records, sealed classes, pattern matching) |
| API | Spring Web MVC (blocking REST + `StreamingResponseBody` for file streaming) |
| Auth | Spring Security + Cookie-based session (HttpOnly) |
| Session store | Spring Session Data Redis |
| ORM | MyBatis-Plus (`TenantLineInnerInterceptor` for row-level tenancy) |
| Primary DB | MySQL / MariaDB |
| Document store | MongoDB (templates, generated docs, GridFS for file storage) |
| Cache / locks | Redis + Redisson (distributed lock, RateLimiter, RTopic pub/sub) |
| Messaging | Apache Kafka + Spring Kafka |
| PDF | Flying Saucer + Thymeleaf (HTML → PDF) |
| Excel | Apache POI |
| CSV | OpenCSV |
| ZIP | Zip4j |
| PDF encryption | Bouncy Castle |
| Email | Spring Mail + Thymeleaf HTML templates |
| Dev infra | Docker Compose (MySQL, MongoDB, Redis, Kafka, Zookeeper) |
| Testing | JUnit 5 + Mockito + Testcontainers |
| Build / DX | Maven · Lombok · MapStruct · Swagger/OpenAPI |

---

## 3. Architecture

Single deployable Spring Boot monolith. All concerns separated by package. No inter-service HTTP calls — Kafka is the only async boundary.

---

## 4. Package Structure

```
com.example.docplatform
├── config/           RedissonConfig, KafkaTopicConfig, AsyncConfig, SwaggerConfig
├── controller/       TenantController, ReportController, ScheduleController
│                     NotificationController, FileController
├── document/         MongoDB docs: GeneratedDocument, ReportTemplate, FileMetadata
├── dto/              request+response DTOs per domain subfolder
├── entity/           MyBatis-Plus entities: Tenant, User, ReportSchedule, AuditLog
├── enums/            ReportType, ReportStatus, FileFormat, DeliveryChannel
├── exception/        GlobalExceptionHandler + custom exceptions
├── filter/           TenantContextFilter
├── kafka/
│   ├── consumer/     ReportJobConsumer, NotificationConsumer
│   ├── event/        ReportRequestedEvent, ReportCompletedEvent
│   └── producer/     ReportJobProducer
├── mapper/           TenantMapper, UserMapper, ReportScheduleMapper, AuditLogMapper
├── notification/     EmailNotificationService, InAppNotificationService
├── report/
│   ├── generator/    PdfReportGenerator, ExcelReportGenerator, CsvReportGenerator
│   └── storage/      DocumentStorageService (MongoDB GridFS)
├── scheduler/        ReportScheduler (@Scheduled cron)
├── security/         SecurityConfig, TenantSecurityInterceptor
├── service/          TenantService, UserService, ReportService,
│                     ScheduleService, FileService
└── tenant/           TenantContextHolder (ThreadLocal)
```

---

## 5. Data Model

### MySQL (MyBatis-Plus)

```sql
tenants
  id BIGINT PK, name VARCHAR, slug VARCHAR UNIQUE,
  plan VARCHAR, created_at DATETIME

users
  id BIGINT PK, tenant_id BIGINT FK, username VARCHAR UNIQUE,
  password_hash VARCHAR, role ENUM('ADMIN','USER'), created_at DATETIME

report_schedules
  id BIGINT PK, tenant_id BIGINT FK, name VARCHAR,
  cron_expr VARCHAR, report_type VARCHAR, format ENUM('PDF','EXCEL','CSV'),
  recipients JSON, params JSON, status ENUM('ACTIVE','PAUSED'),
  last_run_at DATETIME, next_run_at DATETIME

audit_logs
  id BIGINT PK, tenant_id BIGINT FK, user_id BIGINT,
  action VARCHAR, resource VARCHAR, detail TEXT, created_at DATETIME
```

### MongoDB

```
report_templates
  _id, tenant_id, name, type, thymeleaf_template (HTML string),
  variables: [], created_at

generated_documents
  _id, tenant_id, schedule_id, file_format, status (PENDING/COMPLETED/FAILED),
  gridfs_file_id, generated_at, delivered_at, error_msg

file_metadata
  _id, tenant_id, original_name, content_type, size_bytes,
  gridfs_file_id, created_at

notifications
  _id, tenant_id, user_id, message, read: false, created_at
```

### Redis

```
session:<session_id>          Spring Session — HttpOnly cookie
tenant:config:<tenant_id>     Cached tenant config (TTL 10 min)
ratelimit:<tenant_id>         Redisson RateLimiter per tenant
```

---

## 6. Core Flows

### Flow 1 — On-demand Report Generation

```
POST /api/reports/generate
  → ReportService acquires Redisson lock  key: "report:{tenant_id}:{schedule_id}"
  → validates tenant + params
  → saves GeneratedDocument (status=PENDING) to MongoDB
  → publishes ReportRequestedEvent → Kafka topic: report.requested
  → releases lock, returns document_id to caller

ReportJobConsumer (Kafka: report.requested)
  → routes to PdfReportGenerator / ExcelReportGenerator / CsvReportGenerator
  → stores output in MongoDB GridFS
  → updates GeneratedDocument (status=COMPLETED, gridfs_file_id)
  → publishes ReportCompletedEvent → Kafka topic: report.completed

NotificationConsumer (Kafka: report.completed)
  → EmailNotificationService: Spring Mail + Thymeleaf HTML template
  → InAppNotificationService:
      1. persists Notification doc to MongoDB (for badge/list polling)
      2. publishes to Redisson RTopic "notifications:{tenant_id}" (real-time push)
         → GET /api/notifications returns unread list from MongoDB
```

### Flow 2 — Scheduled Report

```
ReportScheduler (@Scheduled every 1 min)
  → queries report_schedules WHERE next_run_at <= NOW AND status = ACTIVE
  → for each due schedule: publishes ReportRequestedEvent (same Kafka flow)
  → updates next_run_at from cron_expr
```

### Flow 3 — File Download

```
GET /api/files/{documentId}
  → FileController verifies session + tenant ownership via TenantContextHolder
  → streams GridFS file via StreamingResponseBody (no full load into memory)
  → sets Content-Disposition: attachment; filename="..."
```

### Flow 4 — Multi-Tenancy Enforcement

```
Every request
  → TenantContextFilter reads tenant_id from Spring Session
  → sets TenantContextHolder (ThreadLocal)
  → MyBatis-Plus TenantLineInnerInterceptor injects WHERE tenant_id = ? automatically
  → ThreadLocal cleared in finally block after response
```

---

## 7. Security

- Session-based auth via Spring Security + Spring Session Data Redis
- HttpOnly cookie (no JS access to session ID)
- `@EnableMethodSecurity` for `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints
- `TenantSecurityInterceptor` verifies the resolved `tenant_id` matches the authenticated user's tenant on every request — prevents cross-tenant data access
- Bouncy Castle used to password-protect PDF exports on request

---

## 8. Key Design Decisions

| Decision | Rationale |
|---|---|
| Redisson lock on report generation | Prevents duplicate jobs from concurrent requests or scheduler ticks firing at the same millisecond |
| MongoDB GridFS for file storage | Avoids storing large binaries in MySQL; supports streaming downloads without loading full file into memory |
| `TenantLineInnerInterceptor` | Eliminates need to manually add `tenant_id` to every query — enforced at ORM layer |
| Kafka as async boundary | Decouples report generation (slow) from HTTP response (fast); consumer can retry on failure |
| Thymeleaf for both PDF source and email | Single template engine for two output channels reduces cognitive overhead |
| Spring Session + Redis | Session data distributed across instances; supports horizontal scaling without sticky sessions |

---

## 9. Out of Scope (this version)

- Kubernetes manifests
- Webhook delivery channel
- Real-time SSE / WebSocket for notifications (RTopic push is fire-and-forget)
- Report template builder UI
