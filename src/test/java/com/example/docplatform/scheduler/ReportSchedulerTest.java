package com.example.docplatform.scheduler;

import com.example.docplatform.dto.assignment.AssignmentRequest;
import com.example.docplatform.entity.ReportSchedule;
import com.example.docplatform.entity.User;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.ScheduleService;
import com.example.docplatform.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportSchedulerTest {

    @Mock ScheduleService scheduleService;
    @Mock AssignmentService assignmentService;
    @Mock UserService userService;
    @InjectMocks ReportScheduler scheduler;

    @Test
    void triggerDueReports_createsAssignmentForEachRecipient() {
        ReportSchedule s = new ReportSchedule();
        s.setId(1L); s.setTenantId(10L); s.setName("Sales");
        s.setFormat(FileFormat.PDF); s.setTemplateId("t1");
        s.setRecipients(List.of("alice")); s.setCronExpr("0 8 * * *");
        s.setCreatedBy(5L);

        User alice = new User(); alice.setId(2L); alice.setUsername("alice");

        when(scheduleService.findDueSchedules()).thenReturn(List.of(s));
        when(userService.findByUsername(10L, "alice")).thenReturn(Optional.of(alice));

        scheduler.triggerDueReports();

        verify(assignmentService).create(eq(10L), eq(5L), any(AssignmentRequest.class));
        verify(scheduleService).recordRun(eq(1L), eq("0 8 * * *"));
    }

    @Test
    void triggerDueReports_skipsScheduleWithNoCreator() {
        ReportSchedule s = new ReportSchedule();
        s.setId(2L); s.setTenantId(10L); s.setName("Old");
        s.setFormat(FileFormat.CSV); s.setTemplateId("t2");
        s.setRecipients(List.of("alice")); s.setCronExpr("0 9 * * *");
        s.setCreatedBy(0L);

        when(scheduleService.findDueSchedules()).thenReturn(List.of(s));

        scheduler.triggerDueReports();

        verify(assignmentService, never()).create(any(), any(), any());
    }
}
