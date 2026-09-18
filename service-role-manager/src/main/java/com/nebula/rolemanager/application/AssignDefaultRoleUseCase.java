package com.nebula.rolemanager.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nebula.rolemanager.application.event.RoleAssignedEvent;
import com.nebula.rolemanager.domain.OutboxEventToPublish;
import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.EventPublisherPort;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;
import com.nebula.rolemanager.event.PlayerRegisteredEvent;

public class AssignDefaultRoleUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignDefaultRoleUseCase.class);
    // Doit rester identique à KafkaTopicConfig.ACCESS_ROLE_ASSIGNED_TOPIC et au
    // routage du connecteur Debezium
    // (service-messaging/debezium/role-manager-outbox-connector.json).
    static final String ACCESS_ROLE_ASSIGNED_TOPIC = "access.role.assigned";
    static final String ROLE_ASSIGNED_EVENT_TYPE = "RoleAssigned";

    private final RoleAssignmentPort roleAssignmentPort;
    private final EventPublisherPort eventPublisherPort;

    public AssignDefaultRoleUseCase(RoleAssignmentPort roleAssignmentPort, EventPublisherPort eventPublisherPort) {
        this.roleAssignmentPort = roleAssignmentPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    /**
     * Attribue le rôle PLAYER en réaction à players.registered.
     * Idempotent : Kafka garantit at-least-once, un rejeu ne doit rien créer.
     */
    public void execute(PlayerRegisteredEvent event) {
        if (event.playerId() == null || event.playerId().isBlank()) {
            throw new IllegalArgumentException("playerId manquant dans players.registered");
        }
        if (roleAssignmentPort.existsByPlayerId(event.playerId())) {
            log.info("Attribution déjà existante pour playerId={}, événement ignoré (idempotence)",
                    event.playerId());
            return;
        }

        RoleAssignment saved = roleAssignmentPort.save(RoleAssignment.defaultFor(event.playerId()));

        // Outbox pattern : l'événement est inséré dans la même transaction que
        // l'attribution (portée par l'adapter d'entrée), Debezium (CDC sur le
        // binlog MySQL) le publie ensuite vers Kafka. Atomicité garantie, pas de
        // dual-write (spec §11).
        eventPublisherPort.publish(new OutboxEventToPublish(
                ACCESS_ROLE_ASSIGNED_TOPIC,
                saved.getPlayerId(),
                ROLE_ASSIGNED_EVENT_TYPE,
                RoleAssignedEvent.from(saved)));
    }
}
