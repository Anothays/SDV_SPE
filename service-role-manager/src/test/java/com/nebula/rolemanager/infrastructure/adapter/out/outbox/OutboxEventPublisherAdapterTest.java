package com.nebula.rolemanager.infrastructure.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nebula.rolemanager.application.event.RoleAssignedEvent;
import com.nebula.rolemanager.domain.OutboxEventToPublish;
import com.nebula.rolemanager.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

class OutboxEventPublisherAdapterTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxEventPublisherAdapter adapter =
            new OutboxEventPublisherAdapter(outboxEventRepository, new ObjectMapper());

    @Test
    void publishSerializesPayloadAndWritesOutboxRow() {
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoleAssignedEvent payload = new RoleAssignedEvent(
                "evt-1", 1, "2026-09-18T10:00:00Z", "uuid-1", Role.MODERATOR);

        adapter.publish(new OutboxEventToPublish("access.role.assigned", "uuid-1", "RoleAssigned", payload));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregatetype()).isEqualTo("access.role.assigned");
        assertThat(saved.getAggregateid()).isEqualTo("uuid-1");
        assertThat(saved.getType()).isEqualTo("RoleAssigned");
        // L'enum est sérialisé en String : c'est ce que service-sso désérialise.
        assertThat(saved.getPayload()).contains("\"playerId\":\"uuid-1\"", "\"role\":\"MODERATOR\"");
        assertThat(saved.getPayload()).doesNotContain("username", "email");
    }
}
