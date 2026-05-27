package com.example.docplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.dto.admin.BootstrapRequest;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.TenantMapper;
import com.example.docplatform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;

    public void bootstrap(BootstrapRequest req) {
        long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, Role.ADMIN));
        if (adminCount > 0) {
            throw new IllegalStateException("System already bootstrapped — an admin user exists");
        }

        Tenant system = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getSlug, "system"));
        if (system == null) {
            system = new Tenant();
            system.setName("System");
            system.setSlug("system");
            system.setPlan("FREE");
            system.setCreatedAt(LocalDateTime.now());
            tenantMapper.insert(system);
        }

        User admin = new User();
        admin.setTenantId(system.getId());
        admin.setUsername(req.username());
        admin.setPasswordHash(passwordEncoder.encode(req.password()));
        admin.setRole(Role.ADMIN);
        admin.setCreatedAt(LocalDateTime.now());
        userMapper.insert(admin);
    }

    public void updateRole(Long userId, Role role) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new ResourceNotFoundException("User not found: " + userId);
        user.setRole(role);
        userMapper.updateById(user);
    }
}
