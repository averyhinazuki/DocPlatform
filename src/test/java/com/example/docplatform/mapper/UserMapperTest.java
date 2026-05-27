package com.example.docplatform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class UserMapperTest {

    @Autowired UserMapper userMapper;
    @Autowired TenantMapper tenantMapper;

    @Test
    void insertAndFindUser() {
        Tenant t = new Tenant();
        t.setName("Acme"); t.setSlug("acme-mapper-test-3"); t.setPlan("FREE");
        tenantMapper.insert(t);

        User u = new User();
        u.setTenantId(t.getId());
        u.setUsername("alice-mapper-test-3");
        u.setPasswordHash("hash");
        u.setRole(Role.USER);
        userMapper.insert(u);

        User found = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, "alice-mapper-test-3"));
        assertThat(found).isNotNull();
        assertThat(found.getTenantId()).isEqualTo(t.getId());
    }
}
