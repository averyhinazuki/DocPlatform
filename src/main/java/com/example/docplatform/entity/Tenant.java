package com.example.docplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tenants")
public class Tenant {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private String slug;
    private String plan;
    private LocalDateTime createdAt;
}
