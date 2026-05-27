package com.example.docplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.dto.auth.LoginRequest;
import com.example.docplatform.dto.auth.RegisterRequest;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.TenantMapper;
import com.example.docplatform.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
    }
}
