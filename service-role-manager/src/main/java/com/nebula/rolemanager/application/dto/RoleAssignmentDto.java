package com.nebula.rolemanager.application.dto;

import java.time.Instant;

import com.nebula.rolemanager.domain.Role;

public record RoleAssignmentDto(
        String playerId,
        Role role,
        Instant assignedAt,
        Instant updatedAt) {
}
