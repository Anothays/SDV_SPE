package com.nebula.rolemanager.infrastructure.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, Long> {

    Optional<RoleAssignmentEntity> findByPlayerId(String playerId);

    boolean existsByPlayerId(String playerId);
}
