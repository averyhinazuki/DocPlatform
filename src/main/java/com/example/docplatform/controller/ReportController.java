package com.example.docplatform.controller;

import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AssignmentService assignmentService;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestBody @Valid ReportRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        String docId = reportService.requestReport(user.tenantId(), req);
        if (req.assignmentId() != null) {
            assignmentService.complete(req.assignmentId(), user.tenantId(), docId);
        }
        return ResponseEntity.accepted().body(Map.of("documentId", docId));
    }
}
