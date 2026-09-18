package com.nebula.rolemanager.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PROFIL_CREATED_TOPIC = "players.profil.created";
    public static final String TELEMETRY_PLAYER_ACTION_TOPIC = "telemetry.player.action";
    // Topic possédé par service-identite : pas de bean NewTopic ici, seulement la constante.
    public static final String PLAYERS_REGISTERED_TOPIC = "players.registered";
    public static final String PLAYERS_REGISTERED_DLT_TOPIC = "players.registered.dlt";

    // Fait métier : volume modéré, chaque message compte
    @Bean
    public NewTopic profilCreatedTopic() {
        return TopicBuilder.name(PROFIL_CREATED_TOPIC)
                .partitions(1)
                .replicas(1)
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
