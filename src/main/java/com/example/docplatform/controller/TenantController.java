package com.example.docplatform.controller;

import com.example.docplatform.dto.tenant.TenantRequest;
import com.example.docplatform.dto.tenant.TenantResponse;
import com.example.docplatform.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest req) {
        return ResponseEntity.status(201).body(tenantService.create(req));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<TenantResponse> list() {
        return tenantService.list();
    }
}
