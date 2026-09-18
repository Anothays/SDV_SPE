package com.nebula.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void accessorsRoundTripAllFields() {
        Instant createdAt = Instant.parse("2026-09-18T10:00:00Z");

        Account account = new Account();
        account.setId("uuid-1");
        account.setUsername("alice");
        account.setEmail("alice@example.com");
        account.setPasswordHash("hash");
        account.setRole("PLAYER");
        account.setRegion("EU");
        account.setCreatedAt(createdAt);

        assertThat(account.getId()).isEqualTo("uuid-1");
        assertThat(account.getUsername()).isEqualTo("alice");
        assertThat(account.getEmail()).isEqualTo("alice@example.com");
        assertThat(account.getPasswordHash()).isEqualTo("hash");
        assertThat(account.getRole()).isEqualTo("PLAYER");
        assertThat(account.getRegion()).isEqualTo("EU");
        assertThat(account.getCreatedAt()).isEqualTo(createdAt);
    }
}
