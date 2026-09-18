package com.nebula.sso.application.event;

/**
 * Contrat access.role.assigned v1, côté consommateur.
 * Dupliqué volontairement depuis service-role-manager (pas de lib partagée) :
 * chaque service possède sa copie du contrat, c'est le découplage.
 * `role` reste un String : sso ne possède pas l'enum des rôles.
 */
public record RoleAssignedEvent(
        String eventId,
        int eventVersion,
        String occurredAt,
        String playerId,
        String role) {
}
