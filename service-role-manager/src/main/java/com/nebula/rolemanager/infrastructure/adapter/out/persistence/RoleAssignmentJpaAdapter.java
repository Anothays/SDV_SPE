package com.nebula.rolemanager.infrastructure.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;

@Repository
public class RoleAssignmentJpaAdapter implements RoleAssignmentPort {

    private final RoleAssignmentRepository repository;

    public RoleAssignmentJpaAdapter(RoleAssignmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public RoleAssignment save(RoleAssignment assignment) {
        RoleAssignmentEntity saved = repository.save(toEntity(assignment));
        return toDomain(saved);
    }

    @Override
    public Optional<RoleAssignment> findByPlayerId(String playerId) {
        return repository.findByPlayerId(playerId).map(RoleAssignmentJpaAdapter::toDomain);
    }

    @Override
    public boolean existsByPlayerId(String playerId) {
        return repository.existsByPlayerId(playerId);
    }

    private static RoleAssignmentEntity toEntity(RoleAssignment assignment) {
        RoleAssignmentEntity entity = new RoleAssignmentEntity();
        entity.setId(assignment.getId());
        entity.setPlayerId(assignment.getPlayerId());
        entity.setRole(assignment.getRole());
        // Merge sur update : préserver assignedAt existant pour ne pas l'écraser à null.
        entity.setAssignedAt(assignment.getAssignedAt());
        entity.setUpdatedAt(assignment.getUpdatedAt());
        return entity;
    }

    private static RoleAssignment toDomain(RoleAssignmentEntity entity) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setId(entity.getId());
        assignment.setPlayerId(entity.getPlayerId());
        assignment.setRole(entity.getRole());
        assignment.setAssignedAt(entity.getAssignedAt());
        assignment.setUpdatedAt(entity.getUpdatedAt());
        return assignment;
    }
}
