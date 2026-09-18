package com.nebula.sso.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PLAYERS_REGISTERED_TOPIC = "players.registered";
    // Topic possédé par service-role-manager : pas de bean NewTopic ici, seulement
    // la constante. Le DLT, lui, appartient au consommateur (ce service).
    public static final String ACCESS_ROLE_ASSIGNED_TOPIC = "access.role.assigned";
    public static final String ACCESS_ROLE_ASSIGNED_DLT_TOPIC = "access.role.assigned.dlt";

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

    // Dead letter : faible volume, rétention longue pour investigation (spec §4)
    @Bean
    public NewTopic accessRoleAssignedDltTopic() {
        return TopicBuilder.name(ACCESS_ROLE_ASSIGNED_DLT_TOPIC)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(14 * 24 * 60 * 60 * 1000L))
                .build();
    }
}
