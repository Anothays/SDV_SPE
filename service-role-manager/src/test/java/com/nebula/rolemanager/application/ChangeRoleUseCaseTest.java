package com.nebula.rolemanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nebula.rolemanager.application.dto.RoleAssignmentDto;
import com.nebula.rolemanager.application.event.RoleAssignedEvent;
import com.nebula.rolemanager.domain.OutboxEventToPublish;
import com.nebula.rolemanager.domain.Role;
import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.EventPublisherPort;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;
import com.nebula.rolemanager.exception.RoleAssignmentNotFoundException;

class ChangeRoleUseCaseTest {

    private RoleAssignmentPort roleAssignmentPort;
    private EventPublisherPort eventPublisherPort;
    private ChangeRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        roleAssignmentPort = mock(RoleAssignmentPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new ChangeRoleUseCase(roleAssignmentPort, eventPublisherPort);
    }

    @Test
    void changesRoleAndPublishesOutboxEvent() {
        RoleAssignment existing = RoleAssignment.defaultFor("uuid-1");
        existing.setId(7L);
        existing.setAssignedAt(Instant.parse("2026-09-18T10:00:00Z"));
        when(roleAssignmentPort.findByPlayerId("uuid-1")).thenReturn(Optional.of(existing));
        when(roleAssignmentPort.save(any(RoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleAssignmentDto dto = useCase.execute("uuid-1", Role.MODERATOR);

        assertThat(dto.playerId()).isEqualTo("uuid-1");
        assertThat(dto.role()).isEqualTo(Role.MODERATOR);
        assertThat(dto.assignedAt()).isEqualTo(Instant.parse("2026-09-18T10:00:00Z"));

        ArgumentCaptor<OutboxEventToPublish> eventCaptor = ArgumentCaptor.forClass(OutboxEventToPublish.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().aggregateType()).isEqualTo("access.role.assigned");
        assertThat(eventCaptor.getValue().aggregateId()).isEqualTo("uuid-1");
        assertThat(((RoleAssignedEvent) eventCaptor.getValue().payload()).role()).isEqualTo(Role.MODERATOR);
    }

    @Test
    void throwsNotFoundForUnknownPlayer() {
        when(roleAssignmentPort.findByPlayerId("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("inconnu", Role.ADMIN))
                .isInstanceOf(RoleAssignmentNotFoundException.class);
        verify(roleAssignmentPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }
}
