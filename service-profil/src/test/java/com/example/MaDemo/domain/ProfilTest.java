package com.example.MaDemo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.domain.Profil;

class ProfilTest {

    @Test
    void accessorsRoundTripAllFields() {
        Instant createdAt = Instant.parse("2026-09-18T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-09-18T11:00:00Z");

        Profil profil = new Profil();
        profil.setId(1L);
        profil.setPlayerId("player-123");
        profil.setUsername("nebula-player");
        profil.setRegion("EU");
        profil.setLevel(5);
        profil.setCreatedAt(createdAt);
        profil.setUpdatedAt(updatedAt);

        assertThat(profil.getId()).isEqualTo(1L);
        assertThat(profil.getPlayerId()).isEqualTo("player-123");
        assertThat(profil.getUsername()).isEqualTo("nebula-player");
        assertThat(profil.getRegion()).isEqualTo("EU");
        assertThat(profil.getLevel()).isEqualTo(5);
        assertThat(profil.getCreatedAt()).isEqualTo(createdAt);
        assertThat(profil.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void defaultLevelIsOne() {
        Profil profil = new Profil();

        assertThat(profil.getLevel()).isEqualTo(1);
    }
}
