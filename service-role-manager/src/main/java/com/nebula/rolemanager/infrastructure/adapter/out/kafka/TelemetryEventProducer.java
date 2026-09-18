package com.nebula.rolemanager.infrastructure.adapter.out.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.nebula.rolemanager.application.dto.TelemetryEventDto;
import com.nebula.rolemanager.infrastructure.config.KafkaTopicConfig;

@Component
public class TelemetryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventProducer.class);

    private final KafkaTemplate<String, TelemetryEventDto> kafkaTemplate;

    public TelemetryEventProducer(KafkaTemplate<String, TelemetryEventDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie un événement de télémétrie, clé = playerId (ordre par joueur).
     * Fort volume : pas de log par message en cas de succès, seulement les échecs.
     */
    public void publishPlayerAction(TelemetryEventDto event) {
        kafkaTemplate.send(KafkaTopicConfig.TELEMETRY_PLAYER_ACTION_TOPIC, event.getPlayerId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Échec de publication télémétrie pour playerId={}", event.getPlayerId(), ex);
                    }
                });
    }
}
