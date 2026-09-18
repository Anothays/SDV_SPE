package com.nebula.rolemanager.infrastructure.config;

import java.util.Map;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import com.nebula.rolemanager.event.PlayerRegisteredEvent;

@Configuration
public class KafkaConsumerConfig {

    /**
     * Factory dédiée à players.registered : désérialise vers le DTO local
     * en ignorant les type headers, et route vers le DLT après épuisement
     * des retries (at-least-once, spec §6).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>
            playerRegisteredKafkaListenerContainerFactory(
                    KafkaProperties kafkaProperties,
                    KafkaTemplate<String, Object> kafkaTemplate) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        JsonDeserializer<PlayerRegisteredEvent> jsonDeserializer =
                new JsonDeserializer<>(PlayerRegisteredEvent.class, false);
        // Sans ce wrapper, une erreur de désérialisation (message corrompu, contrat
        // producteur cassé...) survient dans poll() avant que le container ait une
        // chance d'agir : la partition reste bloquée pour toujours sur le même
        // message au lieu de suivre le circuit retry+DLT ci-dessous.
        ErrorHandlingDeserializer<PlayerRegisteredEvent> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), valueDeserializer));

        // Le DLT n'a qu'une partition : on n'hérite pas de la partition d'origine
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopicConfig.PLAYERS_REGISTERED_DLT_TOPIC, 0));

        // 3 retries avec backoff exponentiel, puis DLT (spec §6)
        var backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxAttempts(3);
        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Message invalide (contrat violé) : erreur non transitoire, retenter est inutile
        // et bloque la partition pour rien — direct au DLT.
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
