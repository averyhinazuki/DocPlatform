package com.example.docplatform.service;

import com.example.docplatform.dto.auth.RegisterRequest;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.TenantMapper;
import com.example.docplatform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Mock TenantMapper tenantMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authManager;
    @InjectMocks AuthService authService;

    @Test
    void register(){
        Tenant t2=new Tenant();
        t2.setId(2L);
        when(tenantMapper.selectOne(any())).thenReturn(t2);      // tenant exists
        when(userMapper.selectCount(any())).thenReturn(0L);
        authService.register(new RegisterRequest("avery", "123456", "hnzk"));
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
        Tenant t = new Tenant(); t.setId(1L);
        when(tenantMapper.selectOne(any())).thenReturn(t);
        when(userMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("alice", "pass", "acme")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("taken");
    }
}
