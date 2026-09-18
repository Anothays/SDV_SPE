package com.nebula.identite.infrastructure.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nebula.identite.domain.OutboxEventToPublish;
import com.fasterxml.jackson.databind.ObjectMapper;

class OutboxEventPublisherAdapterTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxEventPublisherAdapter adapter =
            new OutboxEventPublisherAdapter(outboxEventRepository, new ObjectMapper());

    private record Payload(String playerId, String username) {
    }

    @Test
    void publishSerializesPayloadAndWritesOutboxRow() {
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.publish(new OutboxEventToPublish(
                "players.registered", "uuid-1", "PlayerRegistered", new Payload("uuid-1", "alice")));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregatetype()).isEqualTo("players.registered");
        assertThat(saved.getAggregateid()).isEqualTo("uuid-1");
        assertThat(saved.getType()).isEqualTo("PlayerRegistered");
        assertThat(saved.getPayload()).contains("\"playerId\":\"uuid-1\"", "\"username\":\"alice\"");
    }
}
