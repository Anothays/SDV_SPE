package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.ProfilDto;
import com.example.event.PlayerRegisteredEvent;
import com.example.outbox.OutboxEvent;
import com.example.outbox.OutboxEventRepository;
import com.example.repository.ProfilRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProfilCreationService {

    private static final Logger log = LoggerFactory.getLogger(ProfilCreationService.class);
    // Doit rester identique à KafkaTopicConfig.PROFIL_CREATED_TOPIC (package-private,
    // inaccessible depuis com.example.service) et à route.topic.replacement du
    // connecteur Debezium (service-messaging/debezium/profil-outbox-connector.json).
    private static final String PROFIL_CREATED_TOPIC = "players.profil.created";

    private final ProfilRepository profilRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ProfilCreationService(ProfilRepository profilRepository, OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.profilRepository = profilRepository;
        this.outboxEventRepository = outboxEventRepository;
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
        if (profilRepository.existsByPlayerId(event.playerId())) {
            log.info("Profil déjà existant pour playerId={}, événement ignoré (idempotence)",
                    event.playerId());
            return;
        }
        ProfilDto dto = new ProfilDto();
        dto.setPlayerId(event.playerId());
        dto.setUsername(event.username());
        dto.setRegion(event.region());
        dto.setLevel(1);
        ProfilDto saved = profilRepository.save(dto);

        // Outbox pattern : l'événement est inséré dans la même transaction que le
        // profil, Debezium (CDC sur le binlog MySQL) le publie ensuite vers Kafka.
        // Atomicité garantie, plus de dual-write (spec §11).
        outboxEventRepository.save(OutboxEvent.of(
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
