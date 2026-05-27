package com.example.docplatform.controller;

import com.example.docplatform.dto.admin.BootstrapRequest;
import com.example.docplatform.dto.user.RoleUpdateRequest;
import com.example.docplatform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @PostMapping("/api/admin/bootstrap")
    public ResponseEntity<Void> bootstrap(@Valid @RequestBody BootstrapRequest req) {
        userService.bootstrap(req);
        return ResponseEntity.status(201).build();
    }

    @PatchMapping("/api/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateRole(@PathVariable Long id,
                                           @Valid @RequestBody RoleUpdateRequest req) {
        userService.updateRole(id, req.role());
        return ResponseEntity.ok().build();
    }
}
