package com.nebula.sso.infrastructure.config;

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

import com.nebula.sso.application.event.RoleAssignedEvent;

@Configuration
public class KafkaConsumerConfig {

    /**
     * Factory dédiée à access.role.assigned : ce topic est alimenté par le
     * router d'événements Debezium (Outbox), pas par un producer Spring — les
     * messages n'ont donc pas de header __TypeId__ : on désérialise vers le
     * contrat local en ignorant les type headers, et on route vers le DLT
     * après épuisement des retries (at-least-once, spec §6).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RoleAssignedEvent>
            roleAssignedKafkaListenerContainerFactory(
                    KafkaProperties kafkaProperties,
                    KafkaTemplate<String, Object> kafkaTemplate) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        JsonDeserializer<RoleAssignedEvent> jsonDeserializer =
                new JsonDeserializer<>(RoleAssignedEvent.class, false);
        // Sans ce wrapper, une erreur de désérialisation (message corrompu, contrat
        // producteur cassé...) survient dans poll() avant que le container ait une
        // chance d'agir : la partition reste bloquée pour toujours sur le même
        // message au lieu de suivre le circuit retry+DLT ci-dessous.
        ErrorHandlingDeserializer<RoleAssignedEvent> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, RoleAssignedEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), valueDeserializer));

        // Le DLT n'a qu'une partition : on n'hérite pas de la partition d'origine
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopicConfig.ACCESS_ROLE_ASSIGNED_DLT_TOPIC, 0));

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
