package com.nebula.rolemanager.infrastructure.adapter.out.persistence;

import java.time.Instant;

import com.nebula.rolemanager.domain.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_assignment")
public class RoleAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiant métier émis par service-sso (players.registered)
    @Column(name = "player_id", nullable = false, unique = true, length = 36)
    private String playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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
