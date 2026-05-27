package com.example.docplatform.dto.user;

import com.example.docplatform.enums.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull Role role) {}
