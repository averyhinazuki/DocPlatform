package com.example.docplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.ScheduleStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "report_schedules", autoResultMap = true)
public class ReportSchedule {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String name;
    private String cronExpr;
    private String reportType;
    private FileFormat format;
    private String templateId;
    @TableField(typeHandler = JacksonTypeHandler.class) private List<String> recipients;
    @TableField(typeHandler = JacksonTypeHandler.class) private Map<String, Object> params;
    private ScheduleStatus status;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
}
