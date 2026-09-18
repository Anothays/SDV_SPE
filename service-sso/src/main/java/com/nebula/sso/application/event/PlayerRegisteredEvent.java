package com.nebula.sso.application.event;

import java.time.Instant;
import java.util.UUID;

import com.nebula.sso.domain.Account;

/**
 * Contrat players.registered v1 (spec §4).
 * Event-carried state transfer : tout ce dont Profil a besoin, rien de plus.
 * Pas de PII : l'email reste confiné au service SSO.
 */
public record PlayerRegisteredEvent(
        String eventId,
        int eventVersion,
        String occurredAt,
        String playerId,
        String username,
        String region) {

    public static PlayerRegisteredEvent from(Account account) {
        return new PlayerRegisteredEvent(
                UUID.randomUUID().toString(),
                1,
                Instant.now().toString(),
                account.getId(),
                account.getUsername(),
                account.getRegion());
    }
}
