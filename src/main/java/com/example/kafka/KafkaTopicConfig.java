package com.example.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PROFIL_CREATED_TOPIC = "profil-created";

    @Bean
    public NewTopic profilCreatedTopic() {
        return TopicBuilder.name(PROFIL_CREATED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
