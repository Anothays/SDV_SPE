package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.PlayerRegisteredEvent;
import com.example.dto.ProfilDto;
import com.example.outbox.OutboxEvent;
import com.example.outbox.OutboxEventDao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProfilCreationService {

    private static final Logger log = LoggerFactory.getLogger(ProfilCreationService.class);
    // Doit rester identique à KafkaTopicConfig.PROFIL_CREATED_TOPIC (package-private,
    // inaccessible depuis com.example.service) et à route.topic.replacement du
    // connecteur Debezium (service-messaging/debezium/profil-outbox-connector.json).
    private static final String PROFIL_CREATED_TOPIC = "players.profil.created";

    private final ProfilDao profilDao;
    private final OutboxEventDao outboxEventDao;
    private final ObjectMapper objectMapper;

    public ProfilCreationService(ProfilDao profilDao, OutboxEventDao outboxEventDao, ObjectMapper objectMapper) {
        this.profilDao = profilDao;
        this.outboxEventDao = outboxEventDao;
        this.objectMapper = objectMapper;
    }

    /**
     * Crée le profil en réaction à players.registered.
     * Idempotent : Kafka garantit at-least-once, un rejeu ne doit rien créer.
     */
    @Transactional
    public void onPlayerRegistered(PlayerRegisteredEvent event) {
        if (event.playerId() == null || event.playerId().isBlank()) {
            throw new IllegalArgumentException("playerId manquant dans players.registered");
        }
        if (profilDao.existsByPlayerId(event.playerId())) {
            log.info("Profil déjà existant pour playerId={}, événement ignoré (idempotence)",
                    event.playerId());
            return;
        }
        ProfilDto dto = new ProfilDto();
        dto.setPlayerId(event.playerId());
        dto.setUsername(event.username());
        dto.setRegion(event.region());
        dto.setLevel(1);
        ProfilDto saved = profilDao.save(dto);

        // Outbox pattern : l'événement est inséré dans la même transaction que le
        // profil, Debezium (CDC sur le binlog MySQL) le publie ensuite vers Kafka.
        // Atomicité garantie, plus de dual-write (spec §11).
        outboxEventDao.save(OutboxEvent.of(
                PROFIL_CREATED_TOPIC,
                saved.getPlayerId(),
                "ProfilCreated",
                toJson(saved)));
    }

    private String toJson(ProfilDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de sérialiser ProfilDto", e);
        }
    }
}
