package com.nebula.rolemanager.domain;

import java.time.Instant;

/**
 * Attribution de rôle d'un joueur : agrégat du domaine RBAC. Un joueur =
 * une attribution (playerId unique), créée à l'inscription avec le rôle
 * PLAYER puis modifiable par un administrateur.
 */
public class RoleAssignment {

    private Long id;
    private String playerId;
    private Role role;
    private Instant assignedAt;
    private Instant updatedAt;

    public static RoleAssignment defaultFor(String playerId) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setPlayerId(playerId);
        assignment.setRole(Role.PLAYER);
        return assignment;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
