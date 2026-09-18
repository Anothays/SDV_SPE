package com.nebula.rolemanager.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // Possédé par ce service : alimenté par le router Debezium (outbox).
    public static final String ACCESS_ROLE_ASSIGNED_TOPIC = "access.role.assigned";
    public static final String TELEMETRY_PLAYER_ACTION_TOPIC = "telemetry.player.action";
    // Topic possédé par service-sso : pas de bean NewTopic ici, seulement la constante.
    public static final String PLAYERS_REGISTERED_TOPIC = "players.registered";
    public static final String PLAYERS_REGISTERED_DLT_TOPIC = "players.registered.dlt";

    // Fait métier : clé playerId (ordre garanti par joueur), 3 partitions,
    // rétention 7 jours (ARCHITECTURE.md §9).
    @Bean
    public NewTopic accessRoleAssignedTopic() {
        return TopicBuilder.name(ACCESS_ROLE_ASSIGNED_TOPIC)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7 * 24 * 60 * 60 * 1000L))
                .build();
    }

    // Télémétrie : fort volume, valeur individuelle faible.
    // 6 partitions (parallélisme des consommateurs), rétention courte 24h.
    @Bean
    public NewTopic telemetryPlayerActionTopic() {
        return TopicBuilder.name(TELEMETRY_PLAYER_ACTION_TOPIC)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(24 * 60 * 60 * 1000L))
                .build();
    }

    // Dead letter : faible volume, rétention longue pour investigation (spec §4)
    @Bean
    public NewTopic playersRegisteredDltTopic() {
        return TopicBuilder.name(PLAYERS_REGISTERED_DLT_TOPIC)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(14 * 24 * 60 * 60 * 1000L))
                .build();
    }
}
