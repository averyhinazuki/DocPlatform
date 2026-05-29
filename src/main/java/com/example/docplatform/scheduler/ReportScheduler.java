package com.example.docplatform.scheduler;

import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.entity.ReportSchedule;
import com.example.docplatform.service.ReportService;
import com.example.docplatform.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final ScheduleService scheduleService;
    private final ReportService reportService;

    @Scheduled(fixedDelay = 60_000)
    public void triggerDueReports() {
        List<ReportSchedule> due = scheduleService.findDueSchedules();
        for (ReportSchedule s : due) {
            try {
                reportService.requestReport(s.getTenantId(), new ReportRequest(
                    s.getId(), s.getReportType(), s.getFormat(),
                    s.getTemplateId(), s.getParams(), s.getRecipients(), null, null), "system");
                scheduleService.recordRun(s.getId(), s.getCronExpr());
            } catch (IllegalStateException e) {
                log.debug("Skipping schedule {} — already queued: {}", s.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to trigger schedule {}: {}", s.getId(), e.getMessage());
            }
        }
    }
}
