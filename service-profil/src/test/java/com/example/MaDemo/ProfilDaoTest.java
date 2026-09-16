package com.example.MaDemo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.ProfilDto;

@SpringBootTest
@Transactional
class ProfilDaoTest {

    @Autowired
    private ProfilDao profilDao;

    @Test
    void savesThenFindsByPlayerId() {
        ProfilDto dto = new ProfilDto();
        dto.setPlayerId("uuid-1");
        dto.setUsername("alice");
        dto.setRegion("EU");
        dto.setLevel(1);

        ProfilDto saved = profilDao.save(dto);

        assertThat(saved.getId()).isNotNull();
        assertThat(profilDao.existsByPlayerId("uuid-1")).isTrue();
        assertThat(profilDao.findByPlayerId("uuid-1"))
                .hasValueSatisfying(p -> {
                    assertThat(p.getUsername()).isEqualTo("alice");
                    assertThat(p.getLevel()).isEqualTo(1);
                    assertThat(p.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void existsByPlayerIdIsFalseForUnknownPlayer()  {
        assertThat(profilDao.existsByPlayerId("inconnu")).isFalse();
    }
}
