package com.nebula.rolemanager.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.nebula.rolemanager.domain.Role;
import com.nebula.rolemanager.domain.RoleAssignment;

class RoleAssignedEventTest {

    @Test
    void fromAssignmentFillsContractV1WithoutPii() {
        RoleAssignment assignment = RoleAssignment.defaultFor("uuid-1");
        assignment.setRole(Role.MODERATOR);

        RoleAssignedEvent event = RoleAssignedEvent.from(assignment);

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(Instant.parse(event.occurredAt())).isBeforeOrEqualTo(Instant.now());
        assertThat(event.playerId()).isEqualTo("uuid-1");
        assertThat(event.role()).isEqualTo(Role.MODERATOR);
    }

    @Test
    void eachEventHasItsOwnId() {
        RoleAssignment assignment = RoleAssignment.defaultFor("uuid-1");

        assertThat(RoleAssignedEvent.from(assignment).eventId())
                .isNotEqualTo(RoleAssignedEvent.from(assignment).eventId());
    }
}
