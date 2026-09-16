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
import com.example.kafka.ProfilEventProducer;
import com.example.service.ProfilCreationService;

class ProfilCreationServiceTest {

    private ProfilDao profilDao;
    private ProfilEventProducer profilEventProducer;
    private ProfilCreationService profilCreationService;

    private static final PlayerRegisteredEvent EVENT = new PlayerRegisteredEvent(
            "evt-1", 1, "2026-07-18T10:00:00Z", "uuid-1", "alice", "EU");

    @BeforeEach
    void setUp() {
        profilDao = mock(ProfilDao.class);
        profilEventProducer = mock(ProfilEventProducer.class);
        profilCreationService = new ProfilCreationService(profilDao, profilEventProducer);
    }

    @Test
    void createsProfilAtLevelOneAndPublishesProfilCreated() {
        when(profilDao.existsByPlayerId("uuid-1")).thenReturn(false);
        when(profilDao.save(any(ProfilDto.class))).thenAnswer(inv -> inv.getArgument(0));

        profilCreationService.onPlayerRegistered(EVENT);

        ArgumentCaptor<ProfilDto> captor = ArgumentCaptor.forClass(ProfilDto.class);
        verify(profilDao).save(captor.capture());
        ProfilDto saved = captor.getValue();
        assertThat(saved.getPlayerId()).isEqualTo("uuid-1");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getRegion()).isEqualTo("EU");
        assertThat(saved.getLevel()).isEqualTo(1);
        verify(profilEventProducer).publishProfilCreated(saved);
    }

    @Test
    void skipsWhenProfilAlreadyExists_idempotence() {
        when(profilDao.existsByPlayerId("uuid-1")).thenReturn(true);

        profilCreationService.onPlayerRegistered(EVENT);

        verify(profilDao, never()).save(any());
        verify(profilEventProducer, never()).publishProfilCreated(any());
    }

    @Test
    void rejectsEventWithoutPlayerId() {
        PlayerRegisteredEvent invalide = new PlayerRegisteredEvent(
                "evt-2", 1, "2026-07-18T10:00:00Z", null, "bob", "EU");

        assertThatThrownBy(() -> profilCreationService.onPlayerRegistered(invalide))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
