package com.example.docplatform.controller;

import com.example.docplatform.document.ReportTemplate;
import com.example.docplatform.dto.template.TemplateDetailResponse;
import com.example.docplatform.dto.template.TemplateRequest;
import com.example.docplatform.dto.template.TemplateResponse;
import com.example.docplatform.repository.ReportTemplateRepository;
import com.example.docplatform.security.TenantUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final ReportTemplateRepository templateRepository;

    @GetMapping
    public List<TemplateResponse> list(@AuthenticationPrincipal TenantUserDetails user) {
        return templateRepository.findByTenantId(user.tenantId()).stream()
            .map(t -> new TemplateResponse(t.getId(), t.getName(), t.getType(), t.getVariables()))
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateDetailResponse> getById(
            @PathVariable String id,
            @AuthenticationPrincipal TenantUserDetails user) {
        return templateRepository.findById(id)
            .filter(t -> t.getTenantId().equals(user.tenantId()))
            .map(t -> ResponseEntity.ok(new TemplateDetailResponse(
                t.getId(), t.getName(), t.getType(),
                t.getVariables(), t.getThymeleafTemplate())))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal TenantUserDetails user) {
        templateRepository.findById(id).ifPresent(t -> {
            if (t.getTenantId().equals(user.tenantId())) {
                templateRepository.deleteById(id);
            }
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody TemplateRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        ReportTemplate t = new ReportTemplate();
        t.setTenantId(user.tenantId());
        t.setName(req.name());
        t.setType(req.type());
        t.setThymeleafTemplate(req.thymeleafTemplate());
        t.setVariables(req.variables() != null ? req.variables() : List.of());
        t.setCreatedAt(LocalDateTime.now());
        templateRepository.save(t);
        return ResponseEntity.status(201)
            .body(new TemplateResponse(t.getId(), t.getName(), t.getType(), t.getVariables()));
    }
}
