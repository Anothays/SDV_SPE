package com.nebula.identite.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class KafkaTopicConfig {

    public static final String PLAYERS_REGISTERED_TOPIC = "players.registered";

    // Fait métier : clé playerId (ordre garanti par joueur), 3 partitions
    // (parallélisme des consommateurs), rétention 7 jours (spec §4).
    @Bean
    public NewTopic playersRegisteredTopic() {
        return TopicBuilder.name(PLAYERS_REGISTERED_TOPIC)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7 * 24 * 60 * 60 * 1000L))
                .build();
    }
}
