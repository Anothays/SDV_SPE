package com.nebula.rolemanager.infrastructure.adapter.in.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.nebula.rolemanager.application.dto.TelemetryEventDto;
import com.nebula.rolemanager.infrastructure.config.KafkaTopicConfig;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Consommateur d'agrégation : la télémétrie n'est pas stockée unitairement,
 * elle est réduite en compteurs Micrometer exposés à Prometheus.
 * concurrency = 3 : trois threads de consommation (parallélisme borné par
 * les 6 partitions du topic).
 */
@Component
public class TelemetryEventConsumer {

    private final MeterRegistry meterRegistry;

    public TelemetryEventConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TELEMETRY_PLAYER_ACTION_TOPIC,
            groupId = "role-manager-telemetry",
            concurrency = "3")
    public void onPlayerAction(TelemetryEventDto event) {
        // Le tag "action" doit rester à faible cardinalité (jump, shoot, ...) :
        // chaque valeur distincte crée une série Prometheus supplémentaire
        meterRegistry.counter("telemetry.player.actions", "action", event.getAction())
                .increment();
    }
}
