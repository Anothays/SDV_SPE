package com.nebula.rolemanager.infrastructure.adapter.in.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.nebula.rolemanager.application.dto.ProfilDto;
import com.nebula.rolemanager.infrastructure.config.KafkaTopicConfig;

@Component
public class ProfilEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProfilEventConsumer.class);

    @KafkaListener(topics = KafkaTopicConfig.PROFIL_CREATED_TOPIC, groupId = "role-manager",
            containerFactory = "profilEventKafkaListenerContainerFactory")
    public void onProfilCreated(ProfilDto profil) {
        log.info("Événement players.profil.created reçu : playerId={}, username={}",
                profil.getPlayerId(), profil.getUsername());
    }
}
