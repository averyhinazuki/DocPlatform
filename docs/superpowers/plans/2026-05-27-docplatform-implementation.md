# Document Processing & Notification Platform — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Multi-tenant platform that generates, schedules, and delivers PDF/Excel/CSV reports via Kafka-driven async jobs with email + in-app notifications.

**Architecture:** Single Spring Boot monolith, package-per-domain. HTTP call saves a pending doc and publishes a Kafka event; a consumer does the heavy lifting and uploads to MinIO. Multi-tenancy enforced at ORM layer via MyBatis-Plus `TenantLineInnerInterceptor`.

**Tech Stack:** Spring Boot 3.4.1 · Java 17 · MySQL + MyBatis-Plus · MongoDB · Redis + Spring Session · Redisson · Kafka · MinIO · openhtmltopdf · Apache POI · OpenCSV · Spring Mail + Thymeleaf · Lombok · MapStruct · Swagger

---

## File Map

```
src/main/java/com/example/docplatform/
├── DocPlatformApplication.java
├── config/          MinioConfig, RedissonConfig, KafkaTopicConfig, AsyncConfig, SwaggerConfig, MyBatisPlusConfig
├── controller/      AuthController, TenantController, ReportController, ScheduleController, FileController, NotificationController
├── document/        GeneratedDocument, ReportTemplate, FileMetadata, Notification  (MongoDB)
├── dto/             auth/, tenant/, report/, schedule/, notification/  (Java records)
├── entity/          Tenant, User, ReportSchedule, AuditLog             (MyBatis-Plus)
├── enums/           Role, FileFormat, ReportStatus, ScheduleStatus
├── exception/       GlobalExceptionHandler, ResourceNotFoundException, TenantAccessDeniedException
├── filter/          TenantContextFilter
├── kafka/
│   ├── consumer/    ReportJobConsumer, NotificationConsumer
│   ├── event/       ReportRequestedEvent, ReportCompletedEvent         (records)
│   └── producer/    ReportJobProducer
├── mapper/          TenantMapper, UserMapper, ReportScheduleMapper, AuditLogMapper
├── notification/    EmailNotificationService, InAppNotificationService
├── report/
│   ├── generator/   ReportGenerator (sealed), PdfReportGenerator, ExcelReportGenerator, CsvReportGenerator
│   └── storage/     DocumentStorageService
├── repository/      GeneratedDocumentRepository, ReportTemplateRepository, NotificationRepository
├── scheduler/       ReportScheduler
├── security/        SecurityConfig, UserDetailsServiceImpl, TenantUserDetails
├── service/         AuthService, TenantService, UserService, ReportService, ScheduleService, FileService
└── tenant/          TenantContextHolder

src/main/resources/
├── application.yml
├── schema.sql
└── templates/email/report-ready.html

docker-compose.yml
pom.xml
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `pom.xml`
- Create: `docker-compose.yml`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/schema.sql`
- Create: `src/main/java/com/example/docplatform/DocPlatformApplication.java`
- Create: `src/test/java/com/example/docplatform/DocPlatformApplicationTests.java`

- [ ] **Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>doc-platform</artifactId>
  <version>0.0.1-SNAPSHOT</version>

  <properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <redisson.version>3.27.2</redisson.version>
    <minio.version>8.5.10</minio.version>
    <openhtmltopdf.version>1.0.10</openhtmltopdf.version>
    <poi.version>5.3.0</poi.version>
    <opencsv.version>5.9</opencsv.version>
    <zip4j.version>2.11.5</zip4j.version>
    <bouncycastle.version>1.78.1</bouncycastle.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <springdoc.version>2.5.0</springdoc.version>
  </properties>

  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-mongodb</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-mail</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.session</groupId><artifactId>spring-session-data-redis</artifactId></dependency>
    <dependency>
      <groupId>com.baomidou</groupId><artifactId>mybatis-plus-boot-starter</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
    <dependency>
      <groupId>org.redisson</groupId><artifactId>redisson-spring-boot-starter</artifactId>
      <version>${redisson.version}</version>
    </dependency>
    <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId></dependency>
    <dependency><groupId>io.minio</groupId><artifactId>minio</artifactId><version>${minio.version}</version></dependency>
    <dependency>
      <groupId>com.openhtmltopdf</groupId><artifactId>openhtmltopdf-pdfbox</artifactId>
      <version>${openhtmltopdf.version}</version>
    </dependency>
    <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId><version>${poi.version}</version></dependency>
    <dependency><groupId>com.opencsv</groupId><artifactId>opencsv</artifactId><version>${opencsv.version}</version></dependency>
    <dependency><groupId>net.lingala.zip4j</groupId><artifactId>zip4j</artifactId><version>${zip4j.version}</version></dependency>
    <dependency>
      <groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId>
      <version>${bouncycastle.version}</version>
    </dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId><version>${mapstruct.version}</version></dependency>
    <dependency>
      <groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
      <version>${springdoc.version}</version>
    </dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>mysql</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>mongodb</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>kafka</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude>
          </excludes>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <source>17</source><target>17</target>
          <annotationProcessorPaths>
            <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>${lombok.version}</version></path>
            <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>${mapstruct.version}</version></path>
            <path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId><version>0.2.0</version></path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Create `docker-compose.yml`**

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: docplatform-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: docplatform
    ports: ["3306:3306"]
    volumes: [mysql-data:/var/lib/mysql]

  mongo:
    image: mongo:7
    container_name: docplatform-mongo
    ports: ["27017:27017"]
    volumes: [mongo-data:/data/db]

  redis:
    image: redis:7
    container_name: docplatform-redis
    ports: ["6379:6379"]

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: docplatform-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: docplatform-kafka
    depends_on: [zookeeper]
    ports: ["9092:9092"]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  minio:
    image: minio/minio:latest
    container_name: docplatform-minio
    command: server /data --console-address ":9001"
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes: [minio-data:/data]

volumes:
  mysql-data:
  mongo-data:
  minio-data:
```

- [ ] **Create `src/main/resources/application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/docplatform?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: root
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  data:
    mongodb.uri: mongodb://localhost:27017/docplatform
    redis:
      host: localhost
      port: 6379
  session:
    store-type: redis
    timeout: 30m
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: docplatform-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.docplatform.kafka.event"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  mail:
    host: localhost
    port: 1025
    properties.mail.smtp.auth: false

mybatis-plus:
  type-aliases-package: com.example.docplatform.entity
  configuration:
    map-underscore-to-camel-case: true

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: reports
```

- [ ] **Create `src/main/resources/schema.sql`**

```sql
CREATE TABLE IF NOT EXISTS tenants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  plan VARCHAR(50) DEFAULT 'FREE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  username VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','USER') DEFAULT 'USER',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS report_schedules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  cron_expr VARCHAR(100) NOT NULL,
  report_type VARCHAR(50) NOT NULL,
  format ENUM('PDF','EXCEL','CSV') NOT NULL,
  template_id VARCHAR(100) NOT NULL,
  recipients JSON,
  params JSON,
  status ENUM('ACTIVE','PAUSED') DEFAULT 'ACTIVE',
  last_run_at DATETIME,
  next_run_at DATETIME
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT,
  action VARCHAR(100) NOT NULL,
  resource VARCHAR(255),
  detail TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Create `DocPlatformApplication.java`**

```java
package com.example.docplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.docplatform.mapper")
@EnableScheduling
public class DocPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocPlatformApplication.class, args);
    }
}
```

- [ ] **Create smoke test, verify it compiles**

```java
// src/test/java/com/example/docplatform/DocPlatformApplicationTests.java
@SpringBootTest
class DocPlatformApplicationTests {
    @Test
    void contextLoads() {}
}
```

- [ ] **Run:** `mvn compile -q`  Expected: BUILD SUCCESS

- [ ] **Commit**
```bash
git add . && git commit -m "chore: project scaffolding — pom, docker-compose, application.yml, schema"
```

---

### Task 2: Enums, Entities & MyBatis-Plus Mappers

**Files:**
- Create: `src/main/java/com/example/docplatform/enums/{Role,FileFormat,ReportStatus,ScheduleStatus}.java`
- Create: `src/main/java/com/example/docplatform/entity/{Tenant,User,ReportSchedule,AuditLog}.java`
- Create: `src/main/java/com/example/docplatform/mapper/{TenantMapper,UserMapper,ReportScheduleMapper,AuditLogMapper}.java`
- Test: `src/test/java/com/example/docplatform/mapper/UserMapperTest.java`

- [ ] **Create enums**

```java
// Role.java
public enum Role { ADMIN, USER }

// FileFormat.java
public enum FileFormat { PDF, EXCEL, CSV }

// ReportStatus.java
public enum ReportStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

// ScheduleStatus.java
public enum ScheduleStatus { ACTIVE, PAUSED }
```

- [ ] **Create entities**

```java
// Tenant.java
@Data
@TableName("tenants")
public class Tenant {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private String slug;
    private String plan;
    private LocalDateTime createdAt;
}

// User.java
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String username;
    private String passwordHash;
    private Role role;
    private LocalDateTime createdAt;
}

// ReportSchedule.java
@Data
@TableName(value = "report_schedules", autoResultMap = true)
public class ReportSchedule {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String name;
    private String cronExpr;
    private String reportType;
    private FileFormat format;
    private String templateId;
    @TableField(typeHandler = JacksonTypeHandler.class) private List<String> recipients;
    @TableField(typeHandler = JacksonTypeHandler.class) private Map<String, Object> params;
    private ScheduleStatus status;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
}

// AuditLog.java
@Data
@TableName("audit_logs")
public class AuditLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long userId;
    private String action;
    private String resource;
    private String detail;
    private LocalDateTime createdAt;
}
```

- [ ] **Create mappers** (one per entity, all identical pattern)

```java
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {}

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // existsByUsername is not built-in — use selectCount
}

@Mapper
public interface ReportScheduleMapper extends BaseMapper<ReportSchedule> {}

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {}
```

- [ ] **Write failing test**

```java
@MybatisPlusTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserMapperTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("docplatform").withUsername("root").withPassword("root");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired UserMapper userMapper;
    @Autowired TenantMapper tenantMapper;

    @Test
    void insertAndFindUser() {
        Tenant t = new Tenant();
        t.setName("Acme"); t.setSlug("acme"); t.setPlan("FREE");
        tenantMapper.insert(t);

        User u = new User();
        u.setTenantId(t.getId()); u.setUsername("alice");
        u.setPasswordHash("hash"); u.setRole(Role.USER);
        userMapper.insert(u);

        User found = userMapper.selectById(u.getId());
        assertThat(found.getUsername()).isEqualTo("alice");
        assertThat(found.getTenantId()).isEqualTo(t.getId());
    }
}
```

- [ ] **Run test:** `mvn test -pl . -Dtest=UserMapperTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: enums, MyBatis-Plus entities and mappers"
```

---

### Task 3: Multi-Tenancy Foundation

**Files:**
- Create: `src/main/java/com/example/docplatform/tenant/TenantContextHolder.java`
- Create: `src/main/java/com/example/docplatform/filter/TenantContextFilter.java`
- Create: `src/main/java/com/example/docplatform/config/MyBatisPlusConfig.java`
- Create: `src/main/java/com/example/docplatform/security/TenantUserDetails.java`
- Test: `src/test/java/com/example/docplatform/tenant/TenantContextHolderTest.java`

- [ ] **Write failing test**

```java
class TenantContextHolderTest {
    @Test
    void setAndGetTenantId() {
        TenantContextHolder.setTenantId(42L);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(42L);
        TenantContextHolder.clear();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void isolatedAcrossThreads() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var ref = new AtomicReference<Long>();
        Thread t = new Thread(() -> ref.set(TenantContextHolder.getTenantId()));
        t.start(); t.join();
        assertThat(ref.get()).isNull(); // other thread sees nothing
        TenantContextHolder.clear();
    }
}
```

- [ ] **Run test:** Expected FAIL (class not found)

- [ ] **Create `TenantContextHolder.java`**

```java
package com.example.docplatform.tenant;

public class TenantContextHolder {
    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) { CONTEXT.set(tenantId); }
    public static Long getTenantId() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
```

- [ ] **Run test:** Expected PASS

- [ ] **Create `TenantUserDetails.java`** — custom UserDetails that carries tenantId

```java
package com.example.docplatform.security;

import com.example.docplatform.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record TenantUserDetails(
    Long userId,
    Long tenantId,
    String username,
    String passwordHash,
    Role role
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

- [ ] **Create `TenantContextFilter.java`** — populates TenantContextHolder from session

```java
package com.example.docplatform.filter;

import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.tenant.TenantContextHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof TenantUserDetails details) {
                TenantContextHolder.setTenantId(details.tenantId());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
```

- [ ] **Create `MyBatisPlusConfig.java`** — auto-injects `tenant_id` in every query

```java
package com.example.docplatform.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.example.docplatform.tenant.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class MyBatisPlusConfig {

    private static final Set<String> TENANT_EXEMPT = Set.of("tenants");

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long id = TenantContextHolder.getTenantId();
                return new LongValue(id != null ? id : 0L);
            }

            @Override
            public String getTenantIdColumn() { return "tenant_id"; }

            @Override
            public boolean ignoreTable(String tableName) {
                return TENANT_EXEMPT.contains(tableName);
            }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
```

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: multi-tenancy — TenantContextHolder, TenantContextFilter, MyBatisPlusConfig"
```

---

### Task 4: Auth & Security

**Files:**
- Create: `src/main/java/com/example/docplatform/security/{SecurityConfig,UserDetailsServiceImpl}.java`
- Create: `src/main/java/com/example/docplatform/service/AuthService.java`
- Create: `src/main/java/com/example/docplatform/controller/AuthController.java`
- Create: `src/main/java/com/example/docplatform/dto/auth/{LoginRequest,RegisterRequest}.java`
- Create: `src/main/java/com/example/docplatform/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/example/docplatform/service/AuthServiceTest.java`

- [ ] **Create DTOs** (Java records)

```java
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
public record RegisterRequest(@NotBlank String username, @NotBlank String password, @NotBlank String tenantSlug) {}
```

- [ ] **Create `UserDetailsServiceImpl.java`**

```java
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) throw new UsernameNotFoundException("User not found: " + username);
        return new TenantUserDetails(user.getId(), user.getTenantId(),
            user.getUsername(), user.getPasswordHash(), user.getRole());
    }
}
```

- [ ] **Create `SecurityConfig.java`**

```java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Unauthorized\"}");
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .addFilterAfter(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
```

- [ ] **Create `AuthService.java`**

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public void register(RegisterRequest req) {
        Tenant tenant = tenantMapper.selectOne(
            new LambdaQueryWrapper<Tenant>().eq(Tenant::getSlug, req.tenantSlug()));
        if (tenant == null) throw new ResourceNotFoundException("Tenant not found: " + req.tenantSlug());

        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (count > 0) throw new IllegalArgumentException("Username already taken");

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
    }

    public void login(LoginRequest req, HttpServletRequest httpReq) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        // Spring Security stores Authentication in session automatically
    }
}
```

- [ ] **Create `AuthController.java`**

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest req,
                                      HttpServletRequest httpReq,
                                      HttpServletResponse httpRes) {
        authService.login(req, httpReq);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        req.getSession(false);
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Create `GlobalExceptionHandler.java`**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException ex) {
        return ResponseEntity.status(400).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generic(Exception ex) {
        return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
    }
}
```

- [ ] **Write and run unit test for AuthService**

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Mock TenantMapper tenantMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authManager;
    @InjectMocks AuthService authService;

    @Test
    void register_throwsWhenTenantNotFound() {
        when(tenantMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("alice", "pass", "no-tenant")))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void register_throwsWhenUsernameTaken() {
        Tenant t = new Tenant(); t.setId(1L);
        when(tenantMapper.selectOne(any())).thenReturn(t);
        when(userMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("alice", "pass", "acme")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("taken");
    }
}
```

- [ ] **Run:** `mvn test -Dtest=AuthServiceTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: auth — session-based login, register, logout, SecurityConfig"
```

---

### Task 5: MongoDB Documents & Repositories

**Files:**
- Create: `src/main/java/com/example/docplatform/document/{GeneratedDocument,ReportTemplate,Notification,FileMetadata}.java`
- Create: `src/main/java/com/example/docplatform/repository/{GeneratedDocumentRepository,ReportTemplateRepository,NotificationRepository,FileMetadataRepository}.java`

- [ ] **Create MongoDB documents**

```java
// GeneratedDocument.java
@Document(collection = "generated_documents")
@Data
public class GeneratedDocument {
    @Id private String id;
    private Long tenantId;
    private Long scheduleId;
    private String fileFormat;
    private ReportStatus status;
    private String minioBucket;
    private String minioObjectKey;
    private LocalDateTime generatedAt;
    private LocalDateTime deliveredAt;
    private String errorMsg;
}

// ReportTemplate.java
@Document(collection = "report_templates")
@Data
public class ReportTemplate {
    @Id private String id;
    private Long tenantId;
    private String name;
    private String type;
    private String thymeleafTemplate;
    private List<String> variables;
    private LocalDateTime createdAt;
}

// Notification.java
@Document(collection = "notifications")
@Data
public class Notification {
    @Id private String id;
    private Long tenantId;
    private Long userId;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}

// FileMetadata.java
@Document(collection = "file_metadata")
@Data
public class FileMetadata {
    @Id private String id;
    private Long tenantId;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private String minioBucket;
    private String minioObjectKey;
    private LocalDateTime createdAt;
}
```

- [ ] **Create repositories**

```java
public interface GeneratedDocumentRepository extends MongoRepository<GeneratedDocument, String> {
    List<GeneratedDocument> findByTenantIdOrderByGeneratedAtDesc(Long tenantId);
    boolean existsByScheduleIdAndStatusIn(Long scheduleId, List<ReportStatus> statuses);
}

public interface ReportTemplateRepository extends MongoRepository<ReportTemplate, String> {
    List<ReportTemplate> findByTenantId(Long tenantId);
    Optional<ReportTemplate> findByIdAndTenantId(String id, Long tenantId);
}

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByTenantIdAndUserIdAndReadFalse(Long tenantId, Long userId);
}

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
    Optional<FileMetadata> findByIdAndTenantId(String id, Long tenantId);
}
```

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: MongoDB documents and repositories"
```

---

### Task 6: MinIO File Storage

**Files:**
- Create: `src/main/java/com/example/docplatform/config/MinioConfig.java`
- Create: `src/main/java/com/example/docplatform/report/storage/DocumentStorageService.java`
- Test: `src/test/java/com/example/docplatform/report/storage/DocumentStorageServiceTest.java`

- [ ] **Write failing test**

```java
@Testcontainers
class DocumentStorageServiceTest {

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest")
        .withCommand("server /data")
        .withEnv("MINIO_ROOT_USER", "minioadmin")
        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
        .withExposedPorts(9000);

    private DocumentStorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        MinioClient client = MinioClient.builder()
            .endpoint(endpoint).credentials("minioadmin", "minioadmin").build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket("reports").build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket("reports").build());
        }
        storageService = new DocumentStorageService(client, "reports");
    }

    @Test
    void uploadAndPresignedUrl() throws Exception {
        byte[] content = "hello".getBytes();
        String key = storageService.upload(1L, "test.txt", content, "text/plain");
        assertThat(key).contains("reports/1/");
        String url = storageService.generatePresignedUrl(key);
        assertThat(url).isNotBlank();
    }
}
```

- [ ] **Run:** Expected FAIL

- [ ] **Create `MinioConfig.java`**

```java
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}") private String endpoint;
    @Value("${minio.access-key}") private String accessKey;
    @Value("${minio.secret-key}") private String secretKey;
    @Value("${minio.bucket}") private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Bean
    public DocumentStorageService documentStorageService(MinioClient client) {
        return new DocumentStorageService(client, bucket);
    }
}
```

- [ ] **Create `DocumentStorageService.java`**

```java
@RequiredArgsConstructor
public class DocumentStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public String upload(Long tenantId, String filename, byte[] content, String contentType) throws Exception {
        ensureBucketExists();
        String objectKey = "reports/" + tenantId + "/" + UUID.randomUUID() + "-" + filename;
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucket).object(objectKey)
            .stream(new ByteArrayInputStream(content), content.length, -1)
            .contentType(contentType).build());
        return objectKey;
    }

    public String generatePresignedUrl(String objectKey) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
            .method(Method.GET).bucket(bucket).object(objectKey)
            .expiry(5, TimeUnit.MINUTES).build());
    }

    public void delete(String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    private void ensureBucketExists() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
```

- [ ] **Run test:** `mvn test -Dtest=DocumentStorageServiceTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: MinIO file storage — DocumentStorageService"
```

---

### Task 7: Report Generators (PDF, Excel, CSV)

**Files:**
- Create: `src/main/java/com/example/docplatform/report/generator/{ReportGenerator,PdfReportGenerator,ExcelReportGenerator,CsvReportGenerator}.java`
- Create: `src/main/java/com/example/docplatform/config/ReportTemplateEngineConfig.java`
- Test: `src/test/java/com/example/docplatform/report/generator/ReportGeneratorTest.java`

- [ ] **Create `ReportTemplateEngineConfig.java`** — separate Thymeleaf engine for string-based MongoDB templates

```java
@Configuration
public class ReportTemplateEngineConfig {

    @Bean("reportEngine")
    public SpringTemplateEngine reportTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
```

- [ ] **Create `ReportGenerator.java`** — sealed interface (Java 17)

```java
package com.example.docplatform.report.generator;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.enums.FileFormat;
import java.util.Map;

public sealed interface ReportGenerator
    permits PdfReportGenerator, ExcelReportGenerator, CsvReportGenerator {

    byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception;
    FileFormat supportedFormat();
}
```

- [ ] **Create `PdfReportGenerator.java`**

```java
@Component
@RequiredArgsConstructor
public final class PdfReportGenerator implements ReportGenerator {

    @Qualifier("reportEngine")
    private final SpringTemplateEngine templateEngine;

    @Override
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        Context ctx = new Context();
        ctx.setVariables(params);
        String html = templateEngine.process(template.getThymeleafTemplate(), ctx);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.PDF; }
}
```

- [ ] **Create `ExcelReportGenerator.java`**

```java
@Component
@RequiredArgsConstructor
public final class ExcelReportGenerator implements ReportGenerator {

    @Override
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Report");

            // Header row from template variables
            Row header = sheet.createRow(0);
            List<String> columns = template.getVariables();
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i));
            }

            // Data row from params
            Row data = sheet.createRow(1);
            for (int i = 0; i < columns.size(); i++) {
                Object val = params.get(columns.get(i));
                data.createCell(i).setCellValue(val != null ? val.toString() : "");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.EXCEL; }
}
```

- [ ] **Create `CsvReportGenerator.java`**

```java
@Component
@RequiredArgsConstructor
public final class CsvReportGenerator implements ReportGenerator {

    @Override
    public byte[] generate(ReportTemplate template, Map<String, Object> params) throws Exception {
        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            List<String> columns = template.getVariables();
            writer.writeNext(columns.toArray(String[]::new));
            String[] row = columns.stream()
                .map(c -> params.getOrDefault(c, "").toString())
                .toArray(String[]::new);
            writer.writeNext(row);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public FileFormat supportedFormat() { return FileFormat.CSV; }
}
```

- [ ] **Write and run tests**

```java
class ReportGeneratorTest {

    private ReportTemplate sampleTemplate() {
        ReportTemplate t = new ReportTemplate();
        t.setName("sales");
        t.setVariables(List.of("revenue", "orders"));
        t.setThymeleafTemplate("""
            <!DOCTYPE html><html><body>
            <p>Revenue: <span th:text="${revenue}">0</span></p>
            <p>Orders: <span th:text="${orders}">0</span></p>
            </body></html>
            """);
        return t;
    }

    @Test
    void csvGeneratorProducesHeaderAndRow() throws Exception {
        CsvReportGenerator gen = new CsvReportGenerator();
        byte[] csv = gen.generate(sampleTemplate(), Map.of("revenue", 5000, "orders", 42));
        String content = new String(csv);
        assertThat(content).contains("revenue,orders").contains("5000").contains("42");
    }

    @Test
    void excelGeneratorProducesNonEmptyBytes() throws Exception {
        ExcelReportGenerator gen = new ExcelReportGenerator();
        byte[] xlsx = gen.generate(sampleTemplate(), Map.of("revenue", 5000, "orders", 42));
        assertThat(xlsx.length).isGreaterThan(0);
    }
}
```

- [ ] **Run:** `mvn test -Dtest=ReportGeneratorTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: report generators — PDF (openhtmltopdf), Excel (POI), CSV (OpenCSV)"
```

---

### Task 8: Kafka Infrastructure + Report Generation Flow

**Files:**
- Create: `src/main/java/com/example/docplatform/config/KafkaTopicConfig.java`
- Create: `src/main/java/com/example/docplatform/kafka/event/{ReportRequestedEvent,ReportCompletedEvent}.java`
- Create: `src/main/java/com/example/docplatform/kafka/producer/ReportJobProducer.java`
- Create: `src/main/java/com/example/docplatform/service/ReportService.java`
- Create: `src/main/java/com/example/docplatform/kafka/consumer/ReportJobConsumer.java`
- Create: `src/main/java/com/example/docplatform/controller/ReportController.java`
- Test: `src/test/java/com/example/docplatform/service/ReportServiceTest.java`

- [ ] **Create Kafka events** (Java records)

```java
// ReportRequestedEvent.java
public record ReportRequestedEvent(
    String documentId,
    Long tenantId,
    Long scheduleId,
    String reportType,
    String fileFormat,
    String templateId,
    Map<String, Object> params,
    List<String> recipients
) {}

// ReportCompletedEvent.java
public record ReportCompletedEvent(
    String documentId,
    Long tenantId,
    String minioObjectKey,
    String minioBucket,
    String fileFormat,
    List<String> recipients
) {}
```

- [ ] **Create `KafkaTopicConfig.java`**

```java
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic reportRequested() {
        return TopicBuilder.name("report.requested").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reportCompleted() {
        return TopicBuilder.name("report.completed").partitions(3).replicas(1).build();
    }
}
```

- [ ] **Create `ReportJobProducer.java`**

```java
@Component
@RequiredArgsConstructor
public class ReportJobProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRequest(ReportRequestedEvent event) {
        kafkaTemplate.send("report.requested", event.documentId(), event);
    }

    public void publishCompleted(ReportCompletedEvent event) {
        kafkaTemplate.send("report.completed", event.documentId(), event);
    }
}
```

- [ ] **Create `ReportService.java`** — acquires Redisson lock, saves pending doc, publishes event

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final RedissonClient redissonClient;
    private final ReportJobProducer producer;
    private final GeneratedDocumentRepository documentRepository;

    public String requestReport(Long tenantId, ReportRequest req) {
        String lockKey = "report:" + tenantId + ":" + req.scheduleId();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Report generation already in progress for this schedule");
            }

            boolean alreadyQueued = documentRepository.existsByScheduleIdAndStatusIn(
                req.scheduleId(), List.of(ReportStatus.PENDING, ReportStatus.IN_PROGRESS));
            if (alreadyQueued) {
                throw new IllegalStateException("Report already queued");
            }

            GeneratedDocument doc = new GeneratedDocument();
            doc.setTenantId(tenantId);
            doc.setScheduleId(req.scheduleId());
            doc.setFileFormat(req.format().name());
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

- [ ] **Create `ReportJobConsumer.java`** — routes to correct generator, uploads to MinIO

```java
@Component
@RequiredArgsConstructor
public class ReportJobConsumer {

    private final List<ReportGenerator> generators;
    private final ReportTemplateRepository templateRepository;
    private final DocumentStorageService storageService;
    private final GeneratedDocumentRepository documentRepository;
    private final ReportJobProducer producer;

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

            byte[] content = generator.generate(template, event.params());

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
            doc.setDeliveredAt(LocalDateTime.now());
            documentRepository.save(doc);

            producer.publishCompleted(new ReportCompletedEvent(
                doc.getId(), event.tenantId(), objectKey, "reports",
                event.fileFormat(), event.recipients()));

        } catch (Exception e) {
            doc.setStatus(ReportStatus.FAILED);
            doc.setErrorMsg(e.getMessage());
            documentRepository.save(doc);
        }
    }
}
```

- [ ] **Create `ReportController.java`**

```java
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestBody @Valid ReportRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        String docId = reportService.requestReport(user.tenantId(), req);
        return ResponseEntity.accepted().body(Map.of("documentId", docId));
    }
}
```

- [ ] **Write and run ReportService unit test**

```java
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock ReportJobProducer producer;
    @Mock GeneratedDocumentRepository documentRepository;
    @InjectMocks ReportService reportService;

    @Test
    void requestReport_acquiresLockAndPublishes() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(documentRepository.existsByScheduleIdAndStatusIn(any(), any())).thenReturn(false);
        when(documentRepository.save(any())).thenAnswer(inv -> {
            GeneratedDocument d = inv.getArgument(0);
            d.setId("doc-1");
            return d;
        });

        String id = reportService.requestReport(1L,
            new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of("a@b.com")));

        assertThat(id).isEqualTo("doc-1");
        verify(producer).publishRequest(any(ReportRequestedEvent.class));
        verify(lock).unlock();
    }

    @Test
    void requestReport_throwsWhenLockNotAcquired() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> reportService.requestReport(1L,
            new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("in progress");
    }
}
```

- [ ] **Run:** `mvn test -Dtest=ReportServiceTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: Kafka events, ReportService (Redisson lock), ReportJobConsumer, ReportController"
```

---

### Task 9: Report Scheduling

**Files:**
- Create: `src/main/java/com/example/docplatform/service/ScheduleService.java`
- Create: `src/main/java/com/example/docplatform/controller/ScheduleController.java`
- Create: `src/main/java/com/example/docplatform/scheduler/ReportScheduler.java`
- Create: `src/main/java/com/example/docplatform/dto/schedule/{ScheduleRequest,ScheduleResponse}.java`
- Test: `src/test/java/com/example/docplatform/scheduler/ReportSchedulerTest.java`

- [ ] **Create DTOs**

```java
public record ScheduleRequest(
    @NotBlank String name,
    @NotBlank String cronExpr,
    @NotBlank String reportType,
    @NotNull FileFormat format,
    @NotBlank String templateId,
    List<String> recipients,
    Map<String, Object> params
) {}

public record ScheduleResponse(
    Long id, String name, String cronExpr, String reportType,
    FileFormat format, String templateId, ScheduleStatus status,
    LocalDateTime nextRunAt
) {}
```

- [ ] **Create `ScheduleService.java`**

```java
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ReportScheduleMapper scheduleMapper;

    public ScheduleResponse create(Long tenantId, ScheduleRequest req) {
        ReportSchedule s = new ReportSchedule();
        s.setTenantId(tenantId);
        s.setName(req.name());
        s.setCronExpr(req.cronExpr());
        s.setReportType(req.reportType());
        s.setFormat(req.format());
        s.setTemplateId(req.templateId());
        s.setRecipients(req.recipients());
        s.setParams(req.params());
        s.setStatus(ScheduleStatus.ACTIVE);
        s.setNextRunAt(computeNextRun(req.cronExpr()));
        scheduleMapper.insert(s);
        return toResponse(s);
    }

    public List<ScheduleResponse> listByTenant(Long tenantId) {
        return scheduleMapper.selectList(
            new LambdaQueryWrapper<ReportSchedule>().eq(ReportSchedule::getTenantId, tenantId))
            .stream().map(this::toResponse).toList();
    }

    public List<ReportSchedule> findDueSchedules() {
        return scheduleMapper.selectList(new LambdaQueryWrapper<ReportSchedule>()
            .eq(ReportSchedule::getStatus, ScheduleStatus.ACTIVE)
            .le(ReportSchedule::getNextRunAt, LocalDateTime.now()));
    }

    public void recordRun(Long scheduleId, String cronExpr) {
        ReportSchedule s = new ReportSchedule();
        s.setId(scheduleId);
        s.setLastRunAt(LocalDateTime.now());
        s.setNextRunAt(computeNextRun(cronExpr));
        scheduleMapper.updateById(s);
    }

    private LocalDateTime computeNextRun(String cronExpr) {
        return CronExpression.parse(cronExpr).next(LocalDateTime.now());
    }

    private ScheduleResponse toResponse(ReportSchedule s) {
        return new ScheduleResponse(s.getId(), s.getName(), s.getCronExpr(),
            s.getReportType(), s.getFormat(), s.getTemplateId(), s.getStatus(), s.getNextRunAt());
    }
}
```

- [ ] **Create `ReportScheduler.java`**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final ScheduleService scheduleService;
    private final ReportService reportService;

    @Scheduled(fixedDelay = 60_000)
    public void triggerDueReports() {
        List<ReportSchedule> due = scheduleService.findDueSchedules();
        for (ReportSchedule s : due) {
            try {
                reportService.requestReport(s.getTenantId(), new ReportRequest(
                    s.getId(), s.getReportType(), s.getFormat(),
                    s.getTemplateId(), s.getParams(), s.getRecipients()));
                scheduleService.recordRun(s.getId(), s.getCronExpr());
            } catch (IllegalStateException e) {
                log.debug("Skipping schedule {} — already queued: {}", s.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to trigger schedule {}: {}", s.getId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Write and run ReportScheduler test**

```java
@ExtendWith(MockitoExtension.class)
class ReportSchedulerTest {

    @Mock ScheduleService scheduleService;
    @Mock ReportService reportService;
    @InjectMocks ReportScheduler scheduler;

    @Test
    void triggerDueReports_publishesEachDueSchedule() throws Exception {
        ReportSchedule s = new ReportSchedule();
        s.setId(1L); s.setTenantId(10L); s.setReportType("SALES");
        s.setFormat(FileFormat.PDF); s.setTemplateId("t1");
        s.setParams(Map.of()); s.setRecipients(List.of("a@b.com"));
        s.setCronExpr("0 8 * * *");
        when(scheduleService.findDueSchedules()).thenReturn(List.of(s));

        scheduler.triggerDueReports();

        verify(reportService).requestReport(eq(10L), any());
        verify(scheduleService).recordRun(eq(1L), eq("0 8 * * *"));
    }

    @Test
    void triggerDueReports_skipsAlreadyQueuedWithoutThrowing() throws Exception {
        ReportSchedule s = new ReportSchedule();
        s.setId(2L); s.setTenantId(10L); s.setReportType("SALES");
        s.setFormat(FileFormat.CSV); s.setTemplateId("t2");
        s.setParams(Map.of()); s.setRecipients(List.of());
        s.setCronExpr("0 9 * * *");
        when(scheduleService.findDueSchedules()).thenReturn(List.of(s));
        when(reportService.requestReport(any(), any())).thenThrow(new IllegalStateException("already queued"));

        assertThatNoException().isThrownBy(scheduler::triggerDueReports);
    }
}
```

- [ ] **Run:** `mvn test -Dtest=ReportSchedulerTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: schedule management — ScheduleService, ScheduleController, ReportScheduler"
```

---

### Task 10: Notification System

**Files:**
- Create: `src/main/java/com/example/docplatform/notification/{EmailNotificationService,InAppNotificationService}.java`
- Create: `src/main/java/com/example/docplatform/kafka/consumer/NotificationConsumer.java`
- Create: `src/main/java/com/example/docplatform/controller/NotificationController.java`
- Create: `src/main/resources/templates/email/report-ready.html`
- Test: `src/test/java/com/example/docplatform/notification/InAppNotificationServiceTest.java`

- [ ] **Create email template `report-ready.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
  <h2>Your report is ready</h2>
  <p>Document ID: <strong th:text="${documentId}">ID</strong></p>
  <p>Format: <strong th:text="${fileFormat}">FORMAT</strong></p>
  <p><a th:href="${downloadUrl}">Download Report</a></p>
</body>
</html>
```

- [ ] **Create `EmailNotificationService.java`**

```java
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    public void sendReportReady(List<String> recipients, String documentId,
                                 String fileFormat, String downloadUrl) {
        Context ctx = new Context();
        ctx.setVariable("documentId", documentId);
        ctx.setVariable("fileFormat", fileFormat);
        ctx.setVariable("downloadUrl", downloadUrl);
        String html = templateEngine.process("email/report-ready", ctx);

        MimeMessage msg = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setSubject("Your report is ready");
            helper.setText(html, true);
            for (String recipient : recipients) {
                helper.setTo(recipient);
                mailSender.send(msg);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email notification", e);
        }
    }
}
```

- [ ] **Create `InAppNotificationService.java`** — persist + RTopic push

```java
@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final RedissonClient redissonClient;
    private final NotificationRepository notificationRepository;
    private final UserMapper userMapper;

    public void send(Long tenantId, List<String> recipientEmails, String message) {
        // Resolve user IDs from emails (username = email in this app)
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
            notificationRepository.save(n);

            // Real-time push — fire and forget
            redissonClient.getTopic("notifications:" + tenantId).publish(message);
        });
    }

    public List<Notification> getUnread(Long tenantId, Long userId) {
        return notificationRepository.findByTenantIdAndUserIdAndReadFalse(tenantId, userId);
    }

    public void markAllRead(Long tenantId, Long userId) {
        notificationRepository.findByTenantIdAndUserIdAndReadFalse(tenantId, userId)
            .forEach(n -> { n.setRead(true); notificationRepository.save(n); });
    }
}
```

- [ ] **Create `NotificationConsumer.java`**

```java
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final EmailNotificationService emailService;
    private final InAppNotificationService inAppService;
    private final DocumentStorageService storageService;

    @KafkaListener(topics = "report.completed", groupId = "docplatform-group")
    public void consume(ReportCompletedEvent event) {
        try {
            String downloadUrl = storageService.generatePresignedUrl(event.minioObjectKey());
            emailService.sendReportReady(event.recipients(), event.documentId(),
                event.fileFormat(), downloadUrl);
            String msg = "Report ready: " + event.documentId() + " (" + event.fileFormat() + ")";
            inAppService.send(event.tenantId(), event.recipients(), msg);
        } catch (Exception e) {
            throw new RuntimeException("Notification delivery failed", e);
        }
    }
}
```

- [ ] **Create `NotificationController.java`**

```java
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
```

- [ ] **Write and run InAppNotificationService test**

```java
@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock RedissonClient redissonClient;
    @Mock RTopic rTopic;
    @Mock NotificationRepository notificationRepository;
    @Mock UserMapper userMapper;
    @InjectMocks InAppNotificationService service;

    @Test
    void send_persistsAndPublishes() {
        User u = new User(); u.setId(5L); u.setTenantId(1L); u.setUsername("a@b.com");
        when(userMapper.selectOne(any())).thenReturn(u);
        when(redissonClient.getTopic(anyString())).thenReturn(rTopic);

        service.send(1L, List.of("a@b.com"), "Report ready");

        verify(notificationRepository).save(argThat(n ->
            n.getTenantId().equals(1L) && n.getUserId().equals(5L) && !n.isRead()));
        verify(rTopic).publish("Report ready");
    }
}
```

- [ ] **Run:** `mvn test -Dtest=InAppNotificationServiceTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: notifications — email (Spring Mail + Thymeleaf), in-app (Redisson RTopic)"
```

---

### Task 11: File Download API

**Files:**
- Create: `src/main/java/com/example/docplatform/service/FileService.java`
- Create: `src/main/java/com/example/docplatform/controller/FileController.java`
- Test: `src/test/java/com/example/docplatform/service/FileServiceTest.java`

- [ ] **Write failing test**

```java
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock GeneratedDocumentRepository documentRepository;
    @Mock DocumentStorageService storageService;
    @InjectMocks FileService fileService;

    @Test
    void getDownloadUrl_returnPresignedUrl() throws Exception {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(1L);
        doc.setStatus(ReportStatus.COMPLETED);
        doc.setMinioObjectKey("reports/1/abc.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(storageService.generatePresignedUrl("reports/1/abc.pdf")).thenReturn("http://minio/signed");

        String url = fileService.getDownloadUrl(1L, "doc-1");

        assertThat(url).isEqualTo("http://minio/signed");
    }

    @Test
    void getDownloadUrl_throwsWhenTenantMismatch() {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId("doc-1"); doc.setTenantId(99L);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileService.getDownloadUrl(1L, "doc-1"))
            .isInstanceOf(TenantAccessDeniedException.class);
    }
}
```

- [ ] **Run test:** Expected FAIL

- [ ] **Create `FileService.java`**

```java
@Service
@RequiredArgsConstructor
public class FileService {

    private final GeneratedDocumentRepository documentRepository;
    private final DocumentStorageService storageService;

    public String getDownloadUrl(Long tenantId, String documentId) throws Exception {
        GeneratedDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (!doc.getTenantId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied to document: " + documentId);
        }

        if (doc.getStatus() != ReportStatus.COMPLETED) {
            throw new IllegalStateException("Document not ready yet: " + doc.getStatus());
        }

        return storageService.generatePresignedUrl(doc.getMinioObjectKey());
    }
}
```

- [ ] **Create `FileController.java`**

```java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping("/{documentId}/url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String documentId,
            @AuthenticationPrincipal TenantUserDetails user) throws Exception {
        String url = fileService.getDownloadUrl(user.tenantId(), documentId);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
```

- [ ] **Run test:** `mvn test -Dtest=FileServiceTest -q`  Expected: PASS

- [ ] **Commit**
```bash
git add src/ && git commit -m "feat: file download API — presigned MinIO URL with tenant ownership check"
```

---

### Task 12: Swagger, Remaining Config, Final Build

**Files:**
- Create: `src/main/java/com/example/docplatform/config/SwaggerConfig.java`
- Create: `src/main/java/com/example/docplatform/config/AsyncConfig.java`

- [ ] **Create `SwaggerConfig.java`**

```java
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("DocPlatform API")
                .description("Document Processing & Notification Platform")
                .version("1.0.0"));
    }
}
```

- [ ] **Create `AsyncConfig.java`**

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("docplatform-async-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Full build:** `mvn clean package -DskipTests -q`  Expected: BUILD SUCCESS

- [ ] **Run all tests:** `mvn test -q`  Expected: All green

- [ ] **Commit**
```bash
git add src/ && git commit -m "chore: Swagger config, async executor, full build verified"
```

---

## Done

All 12 tasks produce a working, testable platform. The interesting resume-differentiating pieces by task:

| Task | Differentiator |
|---|---|
| 3 | MyBatis-Plus `TenantLineInnerInterceptor` — automatic `WHERE tenant_id=?` |
| 4 | Session-based auth via Spring Session + Redis (contrast to JWT) |
| 6 | MinIO presigned URLs — not streaming through app server |
| 7 | Sealed `ReportGenerator` interface (Java 17) + `openhtmltopdf` |
| 8 | Redisson distributed lock prevents duplicate Kafka job submissions |
| 10 | Redisson RTopic pub/sub for real-time in-app notifications |
