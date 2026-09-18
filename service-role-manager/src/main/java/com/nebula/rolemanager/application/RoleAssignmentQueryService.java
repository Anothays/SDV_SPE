package com.nebula.rolemanager.application;

import com.nebula.rolemanager.application.dto.RoleAssignmentDto;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;
import com.nebula.rolemanager.exception.RoleAssignmentNotFoundException;

public class RoleAssignmentQueryService {

    private final RoleAssignmentPort roleAssignmentPort;

    public RoleAssignmentQueryService(RoleAssignmentPort roleAssignmentPort) {
        this.roleAssignmentPort = roleAssignmentPort;
    }

    public RoleAssignmentDto findByPlayerId(String playerId) {
        return roleAssignmentPort.findByPlayerId(playerId)
                .map(RoleAssignmentMapper::toDto)
                .orElseThrow(() -> new RoleAssignmentNotFoundException(playerId));
    }
}
