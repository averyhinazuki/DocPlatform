package com.example.docplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_logs")
public class AuditLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long userId;
    private String action;
    private String resource;
    private String detail;
    private LocalDateTime createdAt;
}
