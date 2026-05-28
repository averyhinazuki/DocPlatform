package com.example.docplatform.service;

import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.exception.ResourceNotFoundException;
import com.example.docplatform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public void updateRole(Long userId, Role role) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new ResourceNotFoundException("User not found: " + userId);
        user.setRole(role);
        userMapper.updateById(user);
    }
}
