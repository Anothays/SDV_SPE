package com.example.MaDemo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.application.ProfilService;
import com.example.domain.Profil;
import com.example.domain.port.out.ProfilPort;
import com.example.application.dto.ProfilDto;
import com.example.exception.ProfilNotFoundException;

class ProfilServiceTest {

    private ProfilPort profilPort;
    private ProfilService profilService;

    @BeforeEach
    void setUp() {
        profilPort = mock(ProfilPort.class);
        profilService = new ProfilService(profilPort);
    }

    @Test
    void findByPlayerIdReturnsDtoWhenFound() {
        Profil profil = new Profil();
        profil.setId(1L);
        profil.setPlayerId("uuid-1");
        profil.setUsername("alice");
        profil.setRegion("EU");
        profil.setLevel(3);
        when(profilPort.findByPlayerId("uuid-1")).thenReturn(Optional.of(profil));

        ProfilDto dto = profilService.findByPlayerId("uuid-1");

        assertThat(dto.getPlayerId()).isEqualTo("uuid-1");
        assertThat(dto.getUsername()).isEqualTo("alice");
        assertThat(dto.getLevel()).isEqualTo(3);
    }

    @Test
    void findByPlayerIdThrowsWhenNotFound() {
        when(profilPort.findByPlayerId("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profilService.findByPlayerId("inconnu"))
                .isInstanceOf(ProfilNotFoundException.class);
    }

    @Test
    void updateRegionSavesUpdatedProfil() {
        Profil existing = new Profil();
        existing.setId(1L);
        existing.setPlayerId("uuid-1");
        existing.setUsername("alice");
        existing.setRegion("EU");
        existing.setLevel(3);
        when(profilPort.findByPlayerId("uuid-1")).thenReturn(Optional.of(existing));
        when(profilPort.save(any(Profil.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfilDto dto = profilService.updateRegion("uuid-1", "NA");

        assertThat(dto.getRegion()).isEqualTo("NA");
        verify(profilPort).save(existing);
    }

    @Test
    void updateRegionThrowsWhenNotFound() {
        when(profilPort.findByPlayerId("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profilService.updateRegion("inconnu", "NA"))
                .isInstanceOf(ProfilNotFoundException.class);
    }
}
