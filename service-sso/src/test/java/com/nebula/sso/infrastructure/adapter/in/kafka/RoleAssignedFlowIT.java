package com.nebula.sso.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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

import com.nebula.sso.application.event.RoleAssignedEvent;
import com.nebula.sso.domain.Account;
import com.nebula.sso.domain.port.out.AccountPort;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 3,
        topics = {"access.role.assigned", "access.role.assigned.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:sso-role-flow;MODE=MySQL;DB_CLOSE_DELAY=-1"})
class RoleAssignedFlowIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AccountPort accountPort;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Account seedPlayer() {
        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setUsername("alice-" + account.getId().substring(0, 8));
        account.setEmail(account.getUsername() + "@example.com");
        account.setPasswordHash("hash");
        account.setRole("PLAYER");
        account.setRegion("EU");
        account.setCreatedAt(Instant.now());
        return accountPort.save(account);
    }

    @Test
    void validEventProjectsRoleOntoAccount() {
        Account account = seedPlayer();

        kafkaTemplate.send("access.role.assigned", account.getId(), new RoleAssignedEvent(
                "evt-ok", 1, "2026-09-18T10:00:00Z", account.getId(), "MODERATOR"));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(accountPort.findById(account.getId()))
                        .hasValueSatisfying(a -> assertThat(a.getRole()).isEqualTo("MODERATOR")));
    }

    @Test
    void invalidEventLandsInDeadLetterTopicAfterRetries() {
        kafkaTemplate.send("access.role.assigned", "sans-id", new RoleAssignedEvent(
                "evt-ko", 1, "2026-09-18T10:00:00Z", null, "ADMIN"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-probe", "true", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "access.role.assigned.dlt");
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, "access.role.assigned.dlt", Duration.ofSeconds(20));
            assertThat(record.value()).contains("\"eventId\":\"evt-ko\"");
        }
    }
}
