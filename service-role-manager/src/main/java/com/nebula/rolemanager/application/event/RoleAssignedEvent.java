package com.nebula.rolemanager.application.event;

import java.time.Instant;
import java.util.UUID;

import com.nebula.rolemanager.domain.Role;
import com.nebula.rolemanager.domain.RoleAssignment;

/**
 * Contrat access.role.assigned v1 (spec §3.4).
 * Event-carried state transfer : tout ce dont service-sso a besoin pour
 * projeter le rôle dans le JWT, rien de plus. Aucune PII : sso retrouve le
 * compte par playerId.
 */
public record RoleAssignedEvent(
        String eventId,
        int eventVersion,
        String occurredAt,
        String playerId,
        Role role) {

    public static RoleAssignedEvent from(RoleAssignment assignment) {
        return new RoleAssignedEvent(
                UUID.randomUUID().toString(),
                1,
                Instant.now().toString(),
                assignment.getPlayerId(),
                assignment.getRole());
    }
}
