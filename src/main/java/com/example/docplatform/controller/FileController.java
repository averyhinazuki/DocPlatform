package com.example.docplatform.controller;

import com.example.docplatform.dto.PageResponse;
import com.example.docplatform.dto.file.DocumentSummary;
import com.example.docplatform.enums.Role;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping
    public PageResponse<DocumentSummary> list(
            @AuthenticationPrincipal TenantUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        if (user.role() == Role.ADMIN) {
            return fileService.listByTenant(user.tenantId(), safePage, safeSize);
        }
        return fileService.listByUser(user.tenantId(), user.userId(), safePage, safeSize);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String documentId,
            @AuthenticationPrincipal TenantUserDetails user) throws Exception {
        fileService.delete(user.tenantId(), documentId, user.userId(), user.role() == Role.ADMIN);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{documentId}/url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String documentId,
            @AuthenticationPrincipal TenantUserDetails user) throws Exception {
        String url = fileService.getDownloadUrl(user.tenantId(), documentId);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
