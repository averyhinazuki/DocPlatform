package com.example.docplatform.controller;

import com.example.docplatform.dto.user.RoleUpdateRequest;
import com.example.docplatform.security.TenantUserDetails;
import com.example.docplatform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/api/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @AuthenticationPrincipal TenantUserDetails user) {
        List<Map<String, Object>> users = userService.listByTenant(user.tenantId()).stream()
            .map(u -> Map.<String, Object>of("id", u.getId(), "username", u.getUsername(), "role", u.getRole()))
            .toList();
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/api/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateRole(@PathVariable Long id,
                                           @Valid @RequestBody RoleUpdateRequest req) {
        userService.updateRole(id, req.role());
        return ResponseEntity.ok().build();
    }
}
