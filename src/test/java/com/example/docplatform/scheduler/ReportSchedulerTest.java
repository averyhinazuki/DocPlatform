package com.example.docplatform.scheduler;

import com.example.docplatform.entity.ReportSchedule;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.service.ReportService;
import com.example.docplatform.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportSchedulerTest {

    @Mock ScheduleService scheduleService;
    @Mock ReportService reportService;
    @InjectMocks ReportScheduler scheduler;

    @Test
    void triggerDueReports_publishesEachDueSchedule() throws Exception {
        ReportSchedule s = new ReportSchedule();
        s.setId(1L); s.setTenantId(10L); s.setReportType("SALES");
        s.setFormat(FileFormat.PDF); s.setTemplateId("t1");
        s.setParams(Map.of()); s.setRecipients(List.of("a@b.com"));
        s.setCronExpr("0 8 * * *");
        when(scheduleService.findDueSchedules()).thenReturn(List.of(s));

        scheduler.triggerDueReports();

        verify(reportService).requestReport(eq(10L), any());
        verify(scheduleService).recordRun(eq(1L), eq("0 8 * * *"));
    }

    @Test
    void triggerDueReports_skipsAlreadyQueuedWithoutThrowing() throws Exception {
        ReportSchedule s = new ReportSchedule();
        s.setId(2L); s.setTenantId(10L); s.setReportType("SALES");
        s.setFormat(FileFormat.CSV); s.setTemplateId("t2");
        s.setParams(Map.of()); s.setRecipients(List.of());
        s.setCronExpr("0 9 * * *");
        when(scheduleService.findDueSchedules()).thenReturn(List.of(s));
        when(reportService.requestReport(any(), any())).thenThrow(new IllegalStateException("already queued"));

        assertThatNoException().isThrownBy(scheduler::triggerDueReports);
    }
}
