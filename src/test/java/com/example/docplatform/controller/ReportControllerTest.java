package com.example.docplatform.controller;

import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.enums.Role;
import com.example.docplatform.exception.TenantQuotaExceededException;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.AttachmentParserService;
import com.example.docplatform.service.QuotaService;
import com.example.docplatform.service.ReportService;
import com.example.docplatform.service.TenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock ReportService reportService;
    @Mock AssignmentService assignmentService;
    @Mock AttachmentParserService attachmentParser;
    @Mock QuotaService quotaService;
    @Mock TenantService tenantService;
    @InjectMocks ReportController controller;

    private TenantUserDetails user() {
        return new TenantUserDetails(1L, 2L, "alice", "pw", Role.USER);
    }

    private ReportRequest req() {
        return new ReportRequest(null, "sales", FileFormat.PDF,
                "tpl1", Map.of(), List.of(), null, null, null);
    }

    @Test
    void generate_acquiresQuotaWithTenantLimit() {
        when(tenantService.getLimit(2L)).thenReturn(3);
        when(reportService.requestReport(anyLong(), any(), any(), anyLong())).thenReturn("doc1");

        controller.generate(req(), null, user());

        verify(quotaService).acquire(2L, 3);
    }

    @Test
    void generate_propagates429WhenQuotaExceeded() {
        when(tenantService.getLimit(2L)).thenReturn(3);
        doThrow(new TenantQuotaExceededException(2L, 3)).when(quotaService).acquire(2L, 3);

        assertThatThrownBy(() -> controller.generate(req(), null, user()))
            .isInstanceOf(TenantQuotaExceededException.class);
        verify(reportService, never()).requestReport(anyLong(), any(), any(), anyLong());
    }
}
