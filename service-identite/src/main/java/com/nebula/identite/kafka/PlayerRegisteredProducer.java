package com.nebula.identite.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlayerRegisteredProducer {

    private static final Logger log = LoggerFactory.getLogger(PlayerRegisteredProducer.class);

    private final KafkaTemplate<String, PlayerRegisteredEvent> kafkaTemplate;

    public PlayerRegisteredProducer(KafkaTemplate<String, PlayerRegisteredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie l'événement d'inscription. Envoi asynchrone : une panne de Kafka
     * ne doit pas faire échouer l'inscription HTTP (limite documentée spec §6,
     * évolution citée : transactional outbox).
     */
    public void publish(PlayerRegisteredEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC, event.playerId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Échec de publication players.registered pour playerId={}",
                                event.playerId(), ex);
                    } else {
                        log.info("Événement players.registered publié : playerId={}, partition={}, offset={}",
                                event.playerId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
