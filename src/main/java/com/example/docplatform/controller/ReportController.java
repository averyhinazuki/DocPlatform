package com.example.docplatform.controller;

import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import com.example.docplatform.service.AttachmentParserService;
import com.example.docplatform.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AssignmentService assignmentService;
    private final AttachmentParserService attachmentParser;

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> generate(
            @RequestPart("request") @Valid ReportRequest req,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal TenantUserDetails user) {

        ReportRequest effectiveReq = req;
        if (file != null && !file.isEmpty()) {
            Map<String, Object> fileParams = attachmentParser.parse(file);
            Map<String, Object> merged = new HashMap<>(fileParams);
            if (req.params() != null) {
                req.params().forEach((k, v) -> {
                    if (v != null && !v.toString().isBlank()) merged.put(k, v);
                });
            }
            effectiveReq = new ReportRequest(
                req.scheduleId(), req.reportType(), req.format(),
                req.templateId(), merged, req.recipients(), req.assignmentId(), req.note(), req.contentOverride()
            );
        }

        String docId = reportService.requestReport(user.tenantId(), effectiveReq, user.username());
        if (effectiveReq.assignmentId() != null) {
            assignmentService.complete(effectiveReq.assignmentId(), user.tenantId(), docId);
        }
        return ResponseEntity.accepted().body(Map.of("documentId", docId));
    }
}
