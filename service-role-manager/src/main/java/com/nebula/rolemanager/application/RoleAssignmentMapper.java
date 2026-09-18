package com.nebula.rolemanager.application;

import com.nebula.rolemanager.application.dto.RoleAssignmentDto;
import com.nebula.rolemanager.domain.RoleAssignment;

public class RoleAssignmentMapper {

    private RoleAssignmentMapper() {
    }

    public static RoleAssignmentDto toDto(RoleAssignment assignment) {
        return new RoleAssignmentDto(
                assignment.getPlayerId(),
                assignment.getRole(),
                assignment.getAssignedAt(),
                assignment.getUpdatedAt());
    }
}
