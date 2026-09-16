package com.example.MaDemo;

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

import com.example.dao.ProfilDao;
import com.example.dto.PlayerRegisteredEvent;
import com.example.dto.ProfilDto;
import com.example.outbox.OutboxEvent;
import com.example.outbox.OutboxEventDao;
import com.example.service.ProfilCreationService;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProfilCreationServiceTest {

    private ProfilDao profilDao;
    private OutboxEventDao outboxEventDao;
    private ProfilCreationService profilCreationService;

    private static final PlayerRegisteredEvent EVENT = new PlayerRegisteredEvent(
            "evt-1", 1, "2026-07-18T10:00:00Z", "uuid-1", "alice", "EU");

    @BeforeEach
    void setUp() {
        profilDao = mock(ProfilDao.class);
        outboxEventDao = mock(OutboxEventDao.class);
        profilCreationService = new ProfilCreationService(profilDao, outboxEventDao, new ObjectMapper());
    }

    @Test
    void createsProfilAtLevelOneAndWritesOutboxEvent() {
        when(profilDao.existsByPlayerId("uuid-1")).thenReturn(false);
        when(profilDao.save(any(ProfilDto.class))).thenAnswer(inv -> inv.getArgument(0));

        profilCreationService.onPlayerRegistered(EVENT);

        ArgumentCaptor<ProfilDto> profilCaptor = ArgumentCaptor.forClass(ProfilDto.class);
        verify(profilDao).save(profilCaptor.capture());
        ProfilDto saved = profilCaptor.getValue();
        assertThat(saved.getPlayerId()).isEqualTo("uuid-1");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getRegion()).isEqualTo("EU");
        assertThat(saved.getLevel()).isEqualTo(1);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventDao).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        // topic = aggregatetype, clé Kafka = aggregateid (playerId) : contrat du
        // connecteur Debezium EventRouter (service-messaging/debezium/profil-outbox-connector.json)
        assertThat(event.getAggregatetype()).isEqualTo("players.profil.created");
        assertThat(event.getAggregateid()).isEqualTo("uuid-1");
        assertThat(event.getPayload()).contains("\"playerId\":\"uuid-1\"", "\"username\":\"alice\"");
    }

    @Test
    void skipsWhenProfilAlreadyExists_idempotence() {
        when(profilDao.existsByPlayerId("uuid-1")).thenReturn(true);

        profilCreationService.onPlayerRegistered(EVENT);

        verify(profilDao, never()).save(any());
        verify(outboxEventDao, never()).save(any());
    }

    @Test
    void rejectsEventWithoutPlayerId() {
        PlayerRegisteredEvent invalide = new PlayerRegisteredEvent(
                "evt-2", 1, "2026-07-18T10:00:00Z", null, "bob", "EU");

        assertThatThrownBy(() -> profilCreationService.onPlayerRegistered(invalide))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
