package com.example.kafka;

import java.util.Map;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.example.dto.PlayerRegisteredEvent;

@Configuration
public class KafkaConsumerConfig {

    /**
     * Factory dédiée à players.registered : désérialise vers le DTO local
     * en ignorant les type headers (le producteur n'en émet pas — aucune
     * classe Java partagée entre services).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>
            playerRegisteredKafkaListenerContainerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        JsonDeserializer<PlayerRegisteredEvent> valueDeserializer =
                new JsonDeserializer<>(PlayerRegisteredEvent.class, false);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), valueDeserializer));
        return factory;
    }
}
