package com.nebula.rolemanager.infrastructure.adapter.in.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.rolemanager.application.CreateProfilUseCase;
import com.nebula.rolemanager.event.PlayerRegisteredEvent;
import com.nebula.rolemanager.infrastructure.config.KafkaTopicConfig;

@Component
public class PlayerRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlayerRegisteredConsumer.class);

    private final CreateProfilUseCase createProfilUseCase;

    public PlayerRegisteredConsumer(CreateProfilUseCase createProfilUseCase) {
        this.createProfilUseCase = createProfilUseCase;
    }

    @KafkaListener(topics = KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC,
            groupId = "role-manager",
            containerFactory = "playerRegisteredKafkaListenerContainerFactory")
    @Transactional
    public void onPlayerRegistered(PlayerRegisteredEvent event) {
        log.info("Événement players.registered reçu : eventId={}, playerId={}",
                event.eventId(), event.playerId());
        createProfilUseCase.execute(event);
    }
}
