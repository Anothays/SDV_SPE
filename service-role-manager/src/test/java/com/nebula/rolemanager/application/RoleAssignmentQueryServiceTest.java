package com.nebula.rolemanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nebula.rolemanager.application.dto.RoleAssignmentDto;
import com.nebula.rolemanager.domain.Role;
import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;
import com.nebula.rolemanager.exception.RoleAssignmentNotFoundException;

class RoleAssignmentQueryServiceTest {

    private final RoleAssignmentPort roleAssignmentPort = mock(RoleAssignmentPort.class);
    private final RoleAssignmentQueryService service = new RoleAssignmentQueryService(roleAssignmentPort);

    @Test
    void returnsDtoForKnownPlayer() {
        when(roleAssignmentPort.findByPlayerId("uuid-1"))
                .thenReturn(Optional.of(RoleAssignment.defaultFor("uuid-1")));

        RoleAssignmentDto dto = service.findByPlayerId("uuid-1");

        assertThat(dto.playerId()).isEqualTo("uuid-1");
        assertThat(dto.role()).isEqualTo(Role.PLAYER);
    }

    @Test
    void throwsNotFoundForUnknownPlayer() {
        when(roleAssignmentPort.findByPlayerId("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByPlayerId("inconnu"))
                .isInstanceOf(RoleAssignmentNotFoundException.class);
    }
}
