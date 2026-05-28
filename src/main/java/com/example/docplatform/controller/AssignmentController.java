package com.example.docplatform.controller;

import com.example.docplatform.dto.assignment.AssignmentRequest;
import com.example.docplatform.dto.assignment.AssignmentResponse;
import com.example.docplatform.dto.assignment.MyAssignmentResponse;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> create(
            @Valid @RequestBody AssignmentRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        assignmentService.create(user.tenantId(), user.userId(), req);
        return ResponseEntity.status(201).build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AssignmentResponse> listAll(@AuthenticationPrincipal TenantUserDetails user) {
        return assignmentService.listByTenant(user.tenantId());
    }

    @GetMapping("/mine")
    public List<MyAssignmentResponse> listMine(@AuthenticationPrincipal TenantUserDetails user) {
        return assignmentService.listMine(user.userId(), user.tenantId());
    }
}
