package com.example.docplatform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.repository.FileMetadataRepository;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import com.example.docplatform.repository.NotificationRepository;
import com.example.docplatform.repository.ReportTemplateRepository;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class UserMapperTest {

    // Mock infrastructure beans unavailable without running services
    @MockBean AuthenticationManager authenticationManager;
    @MockBean PasswordEncoder passwordEncoder;
    @MockBean JavaMailSender mailSender;
    @MockBean RedissonClient redissonClient;
    @SuppressWarnings("rawtypes") @MockBean KafkaTemplate kafkaTemplate;
    @MockBean ReportTemplateRepository reportTemplateRepository;
    @MockBean GeneratedDocumentRepository generatedDocumentRepository;
    @MockBean NotificationRepository notificationRepository;
    @MockBean FileMetadataRepository fileMetadataRepository;

    @Autowired UserMapper userMapper;
    @Autowired TenantMapper tenantMapper;

    @Test
    void insertAndFindUser() {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        Tenant t = new Tenant();
        t.setName("Acme"); t.setSlug("acme-" + suffix); t.setPlan("FREE");
        tenantMapper.insert(t);

        User u = new User();
        u.setTenantId(t.getId());
        u.setUsername("alice-" + suffix);
        u.setPasswordHash("hash");
        u.setRole(Role.USER);
        userMapper.insert(u);

        User found = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, "alice-" + suffix));
        assertThat(found).isNotNull();
    }
}
