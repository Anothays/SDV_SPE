package com.nebula.rolemanager.application.dto;

import jakarta.validation.constraints.NotBlank;

public class TelemetryEventDto {

    @NotBlank
    private String playerId;

    @NotBlank
    private String action;

    private String occurredAt;

    public TelemetryEventDto() {
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }
}
