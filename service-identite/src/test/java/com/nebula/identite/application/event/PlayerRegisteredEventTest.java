package com.nebula.identite.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.nebula.identite.domain.Account;

class PlayerRegisteredEventTest {

    @Test
    void fromAccountFillsEnvelopeWithoutPii() {
        Account account = new Account();
        account.setId("uuid-1");
        account.setUsername("alice");
        account.setEmail("alice@example.com");
        account.setRegion("EU");

        PlayerRegisteredEvent event = PlayerRegisteredEvent.from(account);

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(Instant.parse(event.occurredAt())).isNotNull(); // ISO-8601 valide
        assertThat(event.playerId()).isEqualTo("uuid-1");
        assertThat(event.username()).isEqualTo("alice");
        assertThat(event.region()).isEqualTo("EU");
        // Contrat spec §4 : l'événement n'a AUCUN champ PII (email, mot de passe)
        assertThat(PlayerRegisteredEvent.class.getRecordComponents())
                .extracting("name")
                .containsExactlyInAnyOrder("eventId", "eventVersion", "occurredAt",
                        "playerId", "username", "region");
    }

    @Test
    void eachEventGetsAFreshEventId() {
        Account account = new Account();
        account.setId("uuid-1");

        assertThat(PlayerRegisteredEvent.from(account).eventId())
                .isNotEqualTo(PlayerRegisteredEvent.from(account).eventId());
    }
}
