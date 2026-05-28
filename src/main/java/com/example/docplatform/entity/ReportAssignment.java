package com.example.docplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.docplatform.enums.AssignmentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_assignments")
public class ReportAssignment {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long createdBy;
    private Long assigneeId;
    private String templateId;
    private String notes;
    private AssignmentStatus status;
    private String documentId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
