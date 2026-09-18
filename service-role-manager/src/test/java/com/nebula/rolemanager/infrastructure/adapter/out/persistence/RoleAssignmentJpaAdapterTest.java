package com.nebula.rolemanager.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.rolemanager.domain.Role;
import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class RoleAssignmentJpaAdapterTest {

    @Autowired
    private RoleAssignmentPort roleAssignmentPort;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesThenFindsByPlayerId() {
        RoleAssignment saved = roleAssignmentPort.save(RoleAssignment.defaultFor("uuid-1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAssignedAt()).isNotNull();
        assertThat(roleAssignmentPort.existsByPlayerId("uuid-1")).isTrue();
        assertThat(roleAssignmentPort.findByPlayerId("uuid-1"))
                .hasValueSatisfying(a -> {
                    assertThat(a.getRole()).isEqualTo(Role.PLAYER);
                    assertThat(a.getAssignedAt()).isNotNull();
                    assertThat(a.getUpdatedAt()).isNull();
                });
    }

    @Test
    void existsByPlayerIdIsFalseForUnknownPlayer() {
        assertThat(roleAssignmentPort.existsByPlayerId("inconnu")).isFalse();
        assertThat(roleAssignmentPort.findByPlayerId("inconnu")).isEmpty();
    }

    @Test
    void updatingRoleKeepsAssignedAtAndSetsUpdatedAt() {
        RoleAssignment created = roleAssignmentPort.save(RoleAssignment.defaultFor("uuid-2"));
        entityManager.flush();
        entityManager.clear();

        RoleAssignment toUpdate = roleAssignmentPort.findByPlayerId("uuid-2").orElseThrow();
        toUpdate.setRole(Role.MODERATOR);
        roleAssignmentPort.save(toUpdate);
        entityManager.flush();
        entityManager.clear();

        assertThat(roleAssignmentPort.findByPlayerId("uuid-2"))
                .hasValueSatisfying(a -> {
                    assertThat(a.getId()).isEqualTo(created.getId());
                    assertThat(a.getRole()).isEqualTo(Role.MODERATOR);
                    // La colonne arrondit à la microseconde : tolérance d'une milliseconde.
                    assertThat(a.getAssignedAt())
                            .isCloseTo(created.getAssignedAt(), within(1, ChronoUnit.MILLIS));
                    assertThat(a.getUpdatedAt()).isNotNull();
                });
    }
}
