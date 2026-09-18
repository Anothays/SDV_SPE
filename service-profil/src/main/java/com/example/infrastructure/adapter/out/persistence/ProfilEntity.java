package com.example.infrastructure.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "profil")
public class ProfilEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiant métier émis par le service Identité (players.registered)
    @Column(name = "player_id", nullable = false, unique = true, length = 36)
    private String playerId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
