package com.nebula.rolemanager.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;
import com.nebula.rolemanager.event.PlayerRegisteredEvent;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 3,
        topics = {"players.registered", "players.registered.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
class PlayerRegisteredFlowIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RoleAssignmentPort roleAssignmentPort;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void validEventAssignsDefaultRole() {
        kafkaTemplate.send("players.registered", "uuid-ok", new PlayerRegisteredEvent(
                "evt-ok", 1, "2026-07-18T10:00:00Z", "uuid-ok", "alice", "EU"));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(roleAssignmentPort.existsByPlayerId("uuid-ok")).isTrue());
    }

    @Test
    void invalidEventLandsInDeadLetterTopicAfterRetries() {
        kafkaTemplate.send("players.registered", "sans-id", new PlayerRegisteredEvent(
                "evt-ko", 1, "2026-07-18T10:00:00Z", null, "bob", "EU"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-probe", "true", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "players.registered.dlt");
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, "players.registered.dlt", Duration.ofSeconds(20));
            assertThat(record.value()).contains("\"eventId\":\"evt-ko\"");
        }
    }
}
