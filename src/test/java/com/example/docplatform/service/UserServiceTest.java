package com.example.docplatform.service;

import com.example.docplatform.entity.User;
import com.example.docplatform.enums.Role;
import com.example.docplatform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @InjectMocks UserService userService;

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

    @Test
    void updateRole_throwsWhenUserNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateRole(99L, Role.ADMIN))
                .isInstanceOf(com.example.docplatform.exception.ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
