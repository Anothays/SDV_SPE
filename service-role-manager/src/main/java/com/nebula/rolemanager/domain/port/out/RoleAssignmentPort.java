package com.nebula.rolemanager.domain.port.out;

import java.util.Optional;

import com.nebula.rolemanager.domain.RoleAssignment;

public interface RoleAssignmentPort {

    RoleAssignment save(RoleAssignment assignment);

    Optional<RoleAssignment> findByPlayerId(String playerId);

    boolean existsByPlayerId(String playerId);
}
