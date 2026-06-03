package com.example.docplatform.scheduler;

import com.example.docplatform.dto.assignment.AssignmentRequest;
import com.example.docplatform.entity.ReportSchedule;
import com.example.docplatform.entity.User;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.ScheduleService;
import com.example.docplatform.service.UserService;
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
    private final AssignmentService assignmentService;
    private final UserService userService;

    @Scheduled(fixedDelay = 60_000)
    public void triggerDueReports() {
        List<ReportSchedule> due = scheduleService.findDueSchedules();
        for (ReportSchedule s : due) {
            try {
                createAssignments(s);
                scheduleService.recordRun(s.getId(), s.getCronExpr());
            } catch (Exception e) {
                log.error("Failed to trigger schedule {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    private void createAssignments(ReportSchedule s) {
        if (s.getRecipients() == null || s.getRecipients().isEmpty()) {
            log.warn("Schedule {} has no recipients — skipping", s.getId());
            return;
        }

        String notes = "[" + s.getName() + "] Preferred format: " + s.getFormat().name();
        Long createdBy = s.getCreatedBy() != null && s.getCreatedBy() > 0 ? s.getCreatedBy() : null;
        if (createdBy == null) {
            log.warn("Schedule {} has no creator recorded — assignments will be skipped", s.getId());
            return;
        }

        for (String username : s.getRecipients()) {
            userService.findByUsername(s.getTenantId(), username).ifPresentOrElse(
                user -> assignmentService.create(s.getTenantId(), createdBy,
                    new AssignmentRequest(user.getId(), s.getTemplateId(), notes)),
                () -> log.warn("Schedule {}: recipient '{}' not found in tenant {}", s.getId(), username, s.getTenantId())
            );
        }
    }
}
