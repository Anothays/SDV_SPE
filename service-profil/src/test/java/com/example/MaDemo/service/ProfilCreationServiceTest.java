package com.example.MaDemo.service;

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

import com.example.dto.ProfilDto;
import com.example.event.PlayerRegisteredEvent;
import com.example.outbox.OutboxEvent;
import com.example.outbox.OutboxEventRepository;
import com.example.repository.ProfilRepository;
import com.example.service.ProfilCreationService;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProfilCreationServiceTest {

    private ProfilRepository profilRepository;
    private OutboxEventRepository outboxEventRepository;
    private ProfilCreationService profilCreationService;

    private static final PlayerRegisteredEvent EVENT = new PlayerRegisteredEvent(
            "evt-1", 1, "2026-07-18T10:00:00Z", "uuid-1", "alice", "EU");

    @BeforeEach
    void setUp() {
        profilRepository = mock(ProfilRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        profilCreationService = new ProfilCreationService(profilRepository, outboxEventRepository, new ObjectMapper());
    }

    @Test
    void createsProfilAtLevelOneAndWritesOutboxEvent() {
        when(profilRepository.existsByPlayerId("uuid-1")).thenReturn(false);
        when(profilRepository.save(any(ProfilDto.class))).thenAnswer(inv -> inv.getArgument(0));

        profilCreationService.onPlayerRegistered(EVENT);

        ArgumentCaptor<ProfilDto> profilCaptor = ArgumentCaptor.forClass(ProfilDto.class);
        verify(profilRepository).save(profilCaptor.capture());
        ProfilDto saved = profilCaptor.getValue();
        assertThat(saved.getPlayerId()).isEqualTo("uuid-1");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getRegion()).isEqualTo("EU");
        assertThat(saved.getLevel()).isEqualTo(1);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        // topic = aggregatetype, clé Kafka = aggregateid (playerId) : contrat du
        // connecteur Debezium EventRouter (service-messaging/debezium/profil-outbox-connector.json)
        assertThat(event.getAggregatetype()).isEqualTo("players.profil.created");
        assertThat(event.getAggregateid()).isEqualTo("uuid-1");
        assertThat(event.getPayload()).contains("\"playerId\":\"uuid-1\"", "\"username\":\"alice\"");
    }

    @Test
    void skipsWhenProfilAlreadyExists_idempotence() {
        when(profilRepository.existsByPlayerId("uuid-1")).thenReturn(true);

        profilCreationService.onPlayerRegistered(EVENT);

        verify(profilRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsEventWithoutPlayerId() {
        PlayerRegisteredEvent invalide = new PlayerRegisteredEvent(
                "evt-2", 1, "2026-07-18T10:00:00Z", null, "bob", "EU");

        assertThatThrownBy(() -> profilCreationService.onPlayerRegistered(invalide))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
