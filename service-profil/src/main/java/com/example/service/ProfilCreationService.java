package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.PlayerRegisteredEvent;
import com.example.dto.ProfilDto;
import com.example.kafka.ProfilEventProducer;

@Service
public class ProfilCreationService {

    private static final Logger log = LoggerFactory.getLogger(ProfilCreationService.class);

    private final ProfilDao profilDao;
    private final ProfilEventProducer profilEventProducer;

    public ProfilCreationService(ProfilDao profilDao, ProfilEventProducer profilEventProducer) {
        this.profilDao = profilDao;
        this.profilEventProducer = profilEventProducer;
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
        profilEventProducer.publishProfilCreated(saved);
    }
}
