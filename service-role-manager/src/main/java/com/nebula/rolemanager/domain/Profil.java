package com.nebula.rolemanager.domain;

import java.time.Instant;

public class Profil {

    private Long id;
    private String playerId;
    private String username;
    private String region;
    private int level = 1;
    private Instant createdAt;
    private Instant updatedAt;

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
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
