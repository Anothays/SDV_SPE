package com.example.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.dto.PlayerRegisteredEvent;
import com.example.service.ProfilCreationService;

@Component
public class PlayerRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlayerRegisteredConsumer.class);

    private final ProfilCreationService profilCreationService;

    public PlayerRegisteredConsumer(ProfilCreationService profilCreationService) {
        this.profilCreationService = profilCreationService;
    }

    @KafkaListener(topics = KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC,
            groupId = "profil-service",
            containerFactory = "playerRegisteredKafkaListenerContainerFactory")
    public void onPlayerRegistered(PlayerRegisteredEvent event) {
        log.info("Événement players.registered reçu : eventId={}, playerId={}",
                event.eventId(), event.playerId());
        profilCreationService.onPlayerRegistered(event);
    }
}
