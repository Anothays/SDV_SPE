package com.nebula.sso.application;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nebula.sso.application.event.RoleAssignedEvent;
import com.nebula.sso.domain.Account;
import com.nebula.sso.domain.port.out.AccountPort;

/**
 * Projette le rôle maître (service-role-manager) dans la table account,
 * pour que le prochain login émette un JWT avec le bon claim "role".
 * Cohérence à terme (spec §3.5) : entre l'événement et sa projection, un
 * login émet encore l'ancien rôle.
 */
public class ApplyRoleAssignmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApplyRoleAssignmentUseCase.class);
    // Copie locale du contrat (pas d'enum partagée avec role-manager) : les trois
    // rôles du dossier d'architecture §10.
    static final Set<String> KNOWN_ROLES = Set.of("PLAYER", "MODERATOR", "ADMIN");

    private final AccountPort accountPort;

    public ApplyRoleAssignmentUseCase(AccountPort accountPort) {
        this.accountPort = accountPort;
    }

    /**
     * Idempotent : Kafka garantit at-least-once, un rejeu ne réécrit rien.
     */
    public void execute(RoleAssignedEvent event) {
        if (event.playerId() == null || event.playerId().isBlank()) {
            throw new IllegalArgumentException("playerId manquant dans access.role.assigned");
        }
        if (event.role() == null || !KNOWN_ROLES.contains(event.role())) {
            // Un rôle inconnu ne doit jamais atteindre le claim JWT : rejet → DLT.
            throw new IllegalArgumentException("role invalide dans access.role.assigned : " + event.role());
        }

        Account account = accountPort.findById(event.playerId()).orElse(null);
        if (account == null) {
            // Pas de retry possible : role-manager n'attribue un rôle qu'à partir d'un
            // players.registered émis par sso après commit du compte. Un compte
            // absent est une incohérence à investiguer, pas un message à rejouer.
            log.warn("Compte introuvable pour playerId={}, événement access.role.assigned ignoré (eventId={})",
                    event.playerId(), event.eventId());
            return;
        }
        if (event.role().equals(account.getRole())) {
            log.info("Rôle {} déjà projeté pour playerId={}, événement ignoré (idempotence)",
                    event.role(), event.playerId());
            return;
        }

        account.setRole(event.role());
        accountPort.save(account);
        log.info("Rôle projeté pour playerId={} : {}", event.playerId(), event.role());
    }
}
