package com.example.docplatform.service;

import com.example.docplatform.dto.admin.BootstrapRequest;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.mapper.TenantMapper;
import com.example.docplatform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock TenantMapper tenantMapper;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    @Test
    void bootstrap_createsAdminWhenNoneExists() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(tenantMapper.selectOne(any())).thenReturn(null);
        when(tenantMapper.insert(any(Tenant.class))).thenAnswer(inv -> {
            ((Tenant) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        userService.bootstrap(new BootstrapRequest("admin", "secret"));

        verify(tenantMapper).insert(any(Tenant.class));
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void bootstrap_throwsWhenAdminAlreadyExists() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> userService.bootstrap(new BootstrapRequest("admin", "secret")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already bootstrapped");
    }

    @Test
    void updateRole_changesUserRole() {
        User user = new User();
        user.setId(5L);
        user.setRole(Role.USER);
        when(userMapper.selectById(5L)).thenReturn(user);

        userService.updateRole(5L, Role.ADMIN);

        verify(userMapper).updateById(user);
        assert user.getRole() == Role.ADMIN;
    }
}
