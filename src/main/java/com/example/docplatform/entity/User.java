package com.example.docplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.docplatform.enums.Role;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String username;
    private String passwordHash;
    private Role role;
    private LocalDateTime createdAt;
}
