package com.nebula.rolemanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nebula.rolemanager.application.event.RoleAssignedEvent;
import com.nebula.rolemanager.domain.OutboxEventToPublish;
import com.nebula.rolemanager.domain.Role;
import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.EventPublisherPort;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;
import com.nebula.rolemanager.event.PlayerRegisteredEvent;

class AssignDefaultRoleUseCaseTest {

    private RoleAssignmentPort roleAssignmentPort;
    private EventPublisherPort eventPublisherPort;
    private AssignDefaultRoleUseCase useCase;

    private static final PlayerRegisteredEvent EVENT = new PlayerRegisteredEvent(
            "evt-1", 1, "2026-09-18T10:00:00Z", "uuid-1", "alice", "EU");

    @BeforeEach
    void setUp() {
        roleAssignmentPort = mock(RoleAssignmentPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new AssignDefaultRoleUseCase(roleAssignmentPort, eventPublisherPort);
    }

    @Test
    void assignsPlayerRoleAndPublishesOutboxEvent() {
        when(roleAssignmentPort.existsByPlayerId("uuid-1")).thenReturn(false);
        when(roleAssignmentPort.save(any(RoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(EVENT);

        ArgumentCaptor<RoleAssignment> assignmentCaptor = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(roleAssignmentPort).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getPlayerId()).isEqualTo("uuid-1");
        assertThat(assignmentCaptor.getValue().getRole()).isEqualTo(Role.PLAYER);

        ArgumentCaptor<OutboxEventToPublish> eventCaptor = ArgumentCaptor.forClass(OutboxEventToPublish.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());
        OutboxEventToPublish published = eventCaptor.getValue();
        // topic = aggregateType, clé Kafka = aggregateId (playerId) : contrat du
        // connecteur Debezium EventRouter (service-messaging/debezium/role-manager-outbox-connector.json)
        assertThat(published.aggregateType()).isEqualTo("access.role.assigned");
        assertThat(published.aggregateId()).isEqualTo("uuid-1");
        assertThat(published.eventType()).isEqualTo("RoleAssigned");
        assertThat(published.payload()).isInstanceOf(RoleAssignedEvent.class);
        RoleAssignedEvent payload = (RoleAssignedEvent) published.payload();
        assertThat(payload.playerId()).isEqualTo("uuid-1");
        assertThat(payload.role()).isEqualTo(Role.PLAYER);
        assertThat(payload.eventVersion()).isEqualTo(1);
    }

    @Test
    void skipsWhenAssignmentAlreadyExists_idempotence() {
        when(roleAssignmentPort.existsByPlayerId("uuid-1")).thenReturn(true);

        useCase.execute(EVENT);

        verify(roleAssignmentPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    void rejectsEventWithoutPlayerId() {
        PlayerRegisteredEvent invalide = new PlayerRegisteredEvent(
                "evt-2", 1, "2026-09-18T10:00:00Z", null, "bob", "EU");

        assertThatThrownBy(() -> useCase.execute(invalide))
                .isInstanceOf(IllegalArgumentException.class);
        verify(eventPublisherPort, never()).publish(any());
    }
}
