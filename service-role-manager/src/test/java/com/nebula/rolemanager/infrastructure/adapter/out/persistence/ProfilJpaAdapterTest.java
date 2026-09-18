package com.nebula.rolemanager.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.rolemanager.domain.Profil;
import com.nebula.rolemanager.domain.port.out.ProfilPort;

@SpringBootTest
@Transactional
class ProfilJpaAdapterTest {

    @Autowired
    private ProfilPort profilPort;

    @Test
    void savesThenFindsByPlayerId() {
        Profil profil = new Profil();
        profil.setPlayerId("uuid-1");
        profil.setUsername("alice");
        profil.setRegion("EU");
        profil.setLevel(1);

        Profil saved = profilPort.save(profil);

        assertThat(saved.getId()).isNotNull();
        assertThat(profilPort.existsByPlayerId("uuid-1")).isTrue();
        assertThat(profilPort.findByPlayerId("uuid-1"))
                .hasValueSatisfying(p -> {
                    assertThat(p.getUsername()).isEqualTo("alice");
                    assertThat(p.getLevel()).isEqualTo(1);
                    assertThat(p.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void existsByPlayerIdIsFalseForUnknownPlayer() {
        assertThat(profilPort.existsByPlayerId("inconnu")).isFalse();
    }
}
