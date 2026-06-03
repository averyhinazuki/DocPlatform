package com.example.docplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public List<User> listByTenant(Long tenantId) {
        return userMapper.selectList(
            new LambdaQueryWrapper<User>().eq(User::getTenantId, tenantId));
    }

    public Optional<User> findByUsername(Long tenantId, String username) {
        return userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getUsername, username))
            .stream().findFirst();
    }

    public void updateRole(Long userId, Role role) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new ResourceNotFoundException("User not found: " + userId);
        user.setRole(role);
        userMapper.updateById(user);
    }
}
