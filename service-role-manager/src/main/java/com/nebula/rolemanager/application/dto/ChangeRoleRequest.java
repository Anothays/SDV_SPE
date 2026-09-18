package com.nebula.rolemanager.application.dto;

import com.nebula.rolemanager.domain.Role;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {
}
