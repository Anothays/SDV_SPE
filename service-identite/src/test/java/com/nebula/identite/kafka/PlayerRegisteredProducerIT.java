package com.nebula.identite.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PlayerRegisteredProducerIT {

    @Autowired
    private PlayerRegisteredProducer producer;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void publishesJsonKeyedByPlayerIdWithoutTypeHeaders() throws Exception {
        producer.publish(new PlayerRegisteredEvent(
                "evt-1", 1, "2026-07-18T10:00:00Z", "p-1", "alice", "EU"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "true", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC);
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC, Duration.ofSeconds(10));

            // Clé = playerId : garantie d'ordre par joueur
            assertThat(record.key()).isEqualTo("p-1");
            // Pas de header de type : aucun nom de classe Java ne fuite
            assertThat(record.headers().lastHeader("__TypeId__")).isNull();

            JsonNode json = new ObjectMapper().readTree(record.value());
            assertThat(json.get("eventId").asText()).isEqualTo("evt-1");
            assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
            assertThat(json.get("playerId").asText()).isEqualTo("p-1");
            assertThat(json.get("username").asText()).isEqualTo("alice");
            assertThat(json.get("region").asText()).isEqualTo("EU");
            assertThat(json.has("email")).isFalse();
        }
    }
}
