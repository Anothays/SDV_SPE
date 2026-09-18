package com.nebula.rolemanager.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RoleAssignmentTest {

    @Test
    void defaultForAssignsPlayerRole() {
        RoleAssignment assignment = RoleAssignment.defaultFor("uuid-1");

        assertThat(assignment.getPlayerId()).isEqualTo("uuid-1");
        assertThat(assignment.getRole()).isEqualTo(Role.PLAYER);
        assertThat(assignment.getId()).isNull();
        assertThat(assignment.getAssignedAt()).isNull();
    }

    @Test
    void roleCanBeChanged() {
        RoleAssignment assignment = RoleAssignment.defaultFor("uuid-1");
        Instant now = Instant.now();

        assignment.setRole(Role.MODERATOR);
        assignment.setAssignedAt(now);
        assignment.setUpdatedAt(now);

        assertThat(assignment.getRole()).isEqualTo(Role.MODERATOR);
        assertThat(assignment.getAssignedAt()).isEqualTo(now);
        assertThat(assignment.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void roleEnumCoversTheThreeArchitectureRoles() {
        assertThat(Role.values()).containsExactly(Role.PLAYER, Role.MODERATOR, Role.ADMIN);
    }
}
