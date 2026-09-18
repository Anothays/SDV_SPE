package com.nebula.rolemanager.application;

import com.nebula.rolemanager.application.dto.RoleAssignmentDto;
import com.nebula.rolemanager.application.event.RoleAssignedEvent;
import com.nebula.rolemanager.domain.OutboxEventToPublish;
import com.nebula.rolemanager.domain.Role;
import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.EventPublisherPort;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;
import com.nebula.rolemanager.exception.RoleAssignmentNotFoundException;

public class ChangeRoleUseCase {

    private final RoleAssignmentPort roleAssignmentPort;
    private final EventPublisherPort eventPublisherPort;

    public ChangeRoleUseCase(RoleAssignmentPort roleAssignmentPort, EventPublisherPort eventPublisherPort) {
        this.roleAssignmentPort = roleAssignmentPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    /**
     * Change le rôle d'un joueur (action d'administration) et publie
     * access.role.assigned via outbox, dans la même transaction. Un rôle
     * identique est republié tel quel : l'idempotence est gérée côté sso.
     */
    public RoleAssignmentDto execute(String playerId, Role role) {
        RoleAssignment assignment = roleAssignmentPort.findByPlayerId(playerId)
                .orElseThrow(() -> new RoleAssignmentNotFoundException(playerId));
        assignment.setRole(role);
        RoleAssignment saved = roleAssignmentPort.save(assignment);

        eventPublisherPort.publish(new OutboxEventToPublish(
                AssignDefaultRoleUseCase.ACCESS_ROLE_ASSIGNED_TOPIC,
                saved.getPlayerId(),
                AssignDefaultRoleUseCase.ROLE_ASSIGNED_EVENT_TYPE,
                RoleAssignedEvent.from(saved)));

        return RoleAssignmentMapper.toDto(saved);
    }
}
