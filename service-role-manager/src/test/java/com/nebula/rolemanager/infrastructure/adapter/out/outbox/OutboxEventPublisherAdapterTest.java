package com.nebula.rolemanager.infrastructure.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nebula.rolemanager.application.dto.ProfilDto;
import com.nebula.rolemanager.domain.OutboxEventToPublish;
import com.nebula.rolemanager.infrastructure.adapter.out.outbox.OutboxEvent;
import com.nebula.rolemanager.infrastructure.adapter.out.outbox.OutboxEventPublisherAdapter;
import com.nebula.rolemanager.infrastructure.adapter.out.outbox.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class OutboxEventPublisherAdapterTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxEventPublisherAdapter adapter =
            new OutboxEventPublisherAdapter(outboxEventRepository, new ObjectMapper());

    @Test
    void publishSerializesPayloadAndWritesOutboxRow() {
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfilDto payload = new ProfilDto();
        payload.setPlayerId("uuid-1");
        payload.setUsername("alice");
        payload.setRegion("EU");
        payload.setLevel(1);

        adapter.publish(new OutboxEventToPublish("players.profil.created", "uuid-1", "ProfilCreated", payload));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregatetype()).isEqualTo("players.profil.created");
        assertThat(saved.getAggregateid()).isEqualTo("uuid-1");
        assertThat(saved.getType()).isEqualTo("ProfilCreated");
        assertThat(saved.getPayload()).contains("\"playerId\":\"uuid-1\"", "\"username\":\"alice\"");
    }
}
