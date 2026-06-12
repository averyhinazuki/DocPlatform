package com.example.docplatform.mapper;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.example.docplatform.config.MyBatisPlusConfig;
import com.example.docplatform.entity.ReportAssignment;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.AssignmentStatus;
import com.example.docplatform.enums.Role;
import com.example.docplatform.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a real MySQL via Testcontainers, lets Flyway apply db/migration,
 * and exercises real mappers with the tenant interceptor active.
 *
 * Named *Test (not *IT) so Maven Surefire runs it as part of `mvn test` / CI.
 * Requires Docker.
 */
@MybatisPlusTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(MyBatisPlusConfig.class)
class MapperIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    UserMapper userMapper;

    @Autowired
    ReportAssignmentMapper assignmentMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void flywaySchemaSupportsUserCrud() {
        User user = new User();
        user.setTenantId(1L);
        user.setUsername("it-user");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        userMapper.insert(user);

        User found = userMapper.selectById(user.getId());
        assertThat(found.getUsername()).isEqualTo("it-user");
        assertThat(found.getTenantId()).isEqualTo(1L);
    }

    @Test
    void tenantPluginIsolatesReportAssignments() {
        TenantContextHolder.setTenantId(1L);
        assignmentMapper.insert(assignment(1L));

        TenantContextHolder.setTenantId(2L);
        assignmentMapper.insert(assignment(2L));

        TenantContextHolder.setTenantId(1L);
        assertThat(assignmentMapper.selectList(null))
                .hasSize(1)
                .allMatch(a -> a.getTenantId().equals(1L));
    }

    @Test
    void missingTenantContextFallsBackToTenantZero() {
        TenantContextHolder.setTenantId(1L);
        assignmentMapper.insert(assignment(1L));

        TenantContextHolder.clear();
        assertThat(assignmentMapper.selectList(null)).isEmpty();
    }

    private ReportAssignment assignment(Long tenantId) {
        ReportAssignment a = new ReportAssignment();
        a.setTenantId(tenantId);
        a.setCreatedBy(10L);
        a.setAssigneeId(20L);
        a.setTemplateId("tpl-1");
        a.setNotes("integration test");
        a.setStatus(AssignmentStatus.PENDING);
        return a;
    }
}
