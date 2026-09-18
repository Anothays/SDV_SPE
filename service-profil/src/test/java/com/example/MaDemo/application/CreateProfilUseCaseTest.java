package com.example.MaDemo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.application.CreateProfilUseCase;
import com.example.domain.OutboxEventToPublish;
import com.example.domain.Profil;
import com.example.domain.port.out.EventPublisherPort;
import com.example.domain.port.out.ProfilPort;
import com.example.application.dto.ProfilDto;
import com.example.event.PlayerRegisteredEvent;

class CreateProfilUseCaseTest {

    private ProfilPort profilPort;
    private EventPublisherPort eventPublisherPort;
    private CreateProfilUseCase createProfilUseCase;

    private static final PlayerRegisteredEvent EVENT = new PlayerRegisteredEvent(
            "evt-1", 1, "2026-07-18T10:00:00Z", "uuid-1", "alice", "EU");

    @BeforeEach
    void setUp() {
        profilPort = mock(ProfilPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        createProfilUseCase = new CreateProfilUseCase(profilPort, eventPublisherPort);
    }

    @Test
    void createsProfilAtLevelOneAndPublishesOutboxEvent() {
        when(profilPort.existsByPlayerId("uuid-1")).thenReturn(false);
        when(profilPort.save(any(Profil.class))).thenAnswer(inv -> inv.getArgument(0));

        createProfilUseCase.execute(EVENT);

        ArgumentCaptor<Profil> profilCaptor = ArgumentCaptor.forClass(Profil.class);
        verify(profilPort).save(profilCaptor.capture());
        Profil saved = profilCaptor.getValue();
        assertThat(saved.getPlayerId()).isEqualTo("uuid-1");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getRegion()).isEqualTo("EU");
        assertThat(saved.getLevel()).isEqualTo(1);

        ArgumentCaptor<OutboxEventToPublish> eventCaptor = ArgumentCaptor.forClass(OutboxEventToPublish.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());
        OutboxEventToPublish published = eventCaptor.getValue();
        // topic = aggregateType, clé Kafka = aggregateId (playerId) : contrat du
        // connecteur Debezium EventRouter (service-messaging/debezium/profil-outbox-connector.json)
        assertThat(published.aggregateType()).isEqualTo("players.profil.created");
        assertThat(published.aggregateId()).isEqualTo("uuid-1");
        assertThat(published.payload()).isInstanceOf(ProfilDto.class);
        assertThat(((ProfilDto) published.payload()).getPlayerId()).isEqualTo("uuid-1");
        assertThat(((ProfilDto) published.payload()).getUsername()).isEqualTo("alice");
    }

    @Test
    void skipsWhenProfilAlreadyExists_idempotence() {
        when(profilPort.existsByPlayerId("uuid-1")).thenReturn(true);

        createProfilUseCase.execute(EVENT);

        verify(profilPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    void rejectsEventWithoutPlayerId() {
        PlayerRegisteredEvent invalide = new PlayerRegisteredEvent(
                "evt-2", 1, "2026-07-18T10:00:00Z", null, "bob", "EU");

        assertThatThrownBy(() -> createProfilUseCase.execute(invalide))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
