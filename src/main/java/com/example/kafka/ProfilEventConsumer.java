package com.example.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.dto.ProfilDto;

@Component
public class ProfilEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProfilEventConsumer.class);

    @KafkaListener(topics = KafkaTopicConfig.PROFIL_CREATED_TOPIC, groupId = "mademo")
    public void onProfilCreated(ProfilDto profil) {
        log.info("Événement profil-created reçu : id={}, name={}", profil.getId(), profil.getName());
    }
}
