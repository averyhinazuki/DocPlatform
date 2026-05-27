package com.example.docplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.dto.schedule.ScheduleRequest;
import com.example.docplatform.dto.schedule.ScheduleResponse;
import com.example.docplatform.entity.ReportSchedule;
import com.example.docplatform.enums.ScheduleStatus;
import com.example.docplatform.mapper.ReportScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ReportScheduleMapper scheduleMapper;

    public ScheduleResponse create(Long tenantId, ScheduleRequest req) {
        ReportSchedule s = new ReportSchedule();
        s.setTenantId(tenantId);
        s.setName(req.name());
        s.setCronExpr(req.cronExpr());
        s.setReportType(req.reportType());
        s.setFormat(req.format());
        s.setTemplateId(req.templateId());
        s.setRecipients(req.recipients());
        s.setParams(req.params());
        s.setStatus(ScheduleStatus.ACTIVE);
        s.setNextRunAt(computeNextRun(req.cronExpr()));
        scheduleMapper.insert(s);
        return toResponse(s);
    }

    public List<ScheduleResponse> listByTenant(Long tenantId) {
        return scheduleMapper.selectList(
            new LambdaQueryWrapper<ReportSchedule>().eq(ReportSchedule::getTenantId, tenantId))
            .stream().map(this::toResponse).toList();
    }

    public List<ReportSchedule> findDueSchedules() {
        return scheduleMapper.selectList(new LambdaQueryWrapper<ReportSchedule>()
            .eq(ReportSchedule::getStatus, ScheduleStatus.ACTIVE)
            .le(ReportSchedule::getNextRunAt, LocalDateTime.now()));
    }

    public void recordRun(Long scheduleId, String cronExpr) {
        ReportSchedule s = new ReportSchedule();
        s.setId(scheduleId);
        s.setLastRunAt(LocalDateTime.now());
        s.setNextRunAt(computeNextRun(cronExpr));
        scheduleMapper.updateById(s);
    }

    private LocalDateTime computeNextRun(String cronExpr) {
        return CronExpression.parse(cronExpr).next(LocalDateTime.now());
    }

    private ScheduleResponse toResponse(ReportSchedule s) {
        return new ScheduleResponse(s.getId(), s.getName(), s.getCronExpr(),
            s.getReportType(), s.getFormat(), s.getTemplateId(), s.getStatus(), s.getNextRunAt());
    }
}
