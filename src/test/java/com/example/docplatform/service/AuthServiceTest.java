package com.example.docplatform.service;

import com.example.docplatform.dto.auth.RegisterRequest;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.TenantMapper;
import com.example.docplatform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Mock TenantMapper tenantMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authManager;
    @InjectMocks AuthService authService;

    private Tenant tenant(long id) {
        Tenant t = new Tenant();
        t.setId(id);
        return t;
    }

    @Test
    void register_firstUserInTenantGetsAdminRole() {
        when(tenantMapper.selectOne(any())).thenReturn(tenant(2L));
        when(userMapper.selectCount(any())).thenReturn(0L); // username free + no existing users

        authService.register(new RegisterRequest("avery", "123456", "hnzk"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void register_secondUserInTenantGetsUserRole() {
        when(tenantMapper.selectOne(any())).thenReturn(tenant(2L));
        // first selectCount (username check) → 0, second (tenant user count) → 1
        when(userMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);

        authService.register(new RegisterRequest("bob", "123456", "hnzk"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void register_throwsWhenTenantNotFound() {
        when(tenantMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("alice", "pass", "no-tenant")))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void register_throwsWhenUsernameTaken() {
        when(tenantMapper.selectOne(any())).thenReturn(tenant(1L));
        when(userMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("alice", "pass", "acme")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("taken");
    }
}
