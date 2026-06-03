package com.example.docplatform.controller;

import com.example.docplatform.dto.schedule.ScheduleRequest;
import com.example.docplatform.dto.schedule.ScheduleResponse;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(
            @Valid @RequestBody ScheduleRequest req,
            @AuthenticationPrincipal TenantUserDetails user) {
        return ResponseEntity.status(201).body(scheduleService.create(user.tenantId(), user.userId(), req));
    }

    @GetMapping
    public List<ScheduleResponse> list(@AuthenticationPrincipal TenantUserDetails user) {
        return scheduleService.listByTenant(user.tenantId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal TenantUserDetails user) {
        scheduleService.delete(id, user.tenantId());
        return ResponseEntity.noContent().build();
    }
}
