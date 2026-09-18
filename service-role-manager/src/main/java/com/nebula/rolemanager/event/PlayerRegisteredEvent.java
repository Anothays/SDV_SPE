package com.nebula.rolemanager.event;

/**
 * Contrat players.registered v1, côté consommateur.
 * Dupliqué volontairement depuis service-sso (pas de lib partagée) :
 * chaque service possède sa copie du contrat, c'est le découplage.
 */
public record PlayerRegisteredEvent(
        String eventId,
        int eventVersion,
        String occurredAt,
        String playerId,
        String username,
        String region) {
}
