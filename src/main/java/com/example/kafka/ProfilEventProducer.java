package com.example.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.dto.ProfilDto;

@Component
public class ProfilEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ProfilEventProducer.class);

    private final KafkaTemplate<String, ProfilDto> kafkaTemplate;

    public ProfilEventProducer(KafkaTemplate<String, ProfilDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie l'événement de création de profil.
     * Envoi asynchrone : une panne de Kafka ne doit pas faire échouer
     * la requête HTTP, l'erreur est seulement journalisée.
     */
    public void publishProfilCreated(ProfilDto profil) {
        kafkaTemplate.send(KafkaTopicConfig.PROFIL_CREATED_TOPIC, String.valueOf(profil.getId()), profil)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Échec de publication de l'événement profil-created pour id={}", profil.getId(), ex);
                    } else {
                        log.info("Événement profil-created publié : id={}, partition={}, offset={}",
                                profil.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
