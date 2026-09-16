package com.example.dto;

/**
 * Contrat players.registered v1, côté consommateur.
 * Dupliqué volontairement depuis service-identite (pas de lib partagée) :
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
