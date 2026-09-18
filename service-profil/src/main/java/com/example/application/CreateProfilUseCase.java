package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.OutboxEventToPublish;
import com.example.domain.Profil;
import com.example.domain.port.out.EventPublisherPort;
import com.example.domain.port.out.ProfilPort;
import com.example.event.PlayerRegisteredEvent;

public class CreateProfilUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateProfilUseCase.class);
    // Doit rester identique à KafkaTopicConfig.PROFIL_CREATED_TOPIC et à
    // route.topic.replacement du connecteur Debezium
    // (service-messaging/debezium/profil-outbox-connector.json).
    private static final String PROFIL_CREATED_TOPIC = "players.profil.created";

    private final ProfilPort profilPort;
    private final EventPublisherPort eventPublisherPort;

    public CreateProfilUseCase(ProfilPort profilPort, EventPublisherPort eventPublisherPort) {
        this.profilPort = profilPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    /**
     * Crée le profil en réaction à players.registered.
     * Idempotent : Kafka garantit at-least-once, un rejeu ne doit rien créer.
     */
    public void execute(PlayerRegisteredEvent event) {
        if (event.playerId() == null || event.playerId().isBlank()) {
            throw new IllegalArgumentException("playerId manquant dans players.registered");
        }
        if (profilPort.existsByPlayerId(event.playerId())) {
            log.info("Profil déjà existant pour playerId={}, événement ignoré (idempotence)",
                    event.playerId());
            return;
        }

        Profil profil = new Profil();
        profil.setPlayerId(event.playerId());
        profil.setUsername(event.username());
        profil.setRegion(event.region());
        profil.setLevel(1);
        Profil saved = profilPort.save(profil);

        // Outbox pattern : l'événement est inséré dans la même transaction que le
        // profil (portée par l'adapter d'entrée, Task 6), Debezium (CDC sur le
        // binlog MySQL) le publie ensuite vers Kafka. Atomicité garantie, plus de
        // dual-write (spec §11).
        eventPublisherPort.publish(new OutboxEventToPublish(
                PROFIL_CREATED_TOPIC,
                saved.getPlayerId(),
                "ProfilCreated",
                ProfilMapper.profilToProfilDto(saved)));
    }
}
