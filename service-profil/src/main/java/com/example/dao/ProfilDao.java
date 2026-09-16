package com.example.dao;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.dto.ProfilDto;
import com.example.entity.Profil;
import com.example.util.DtoEntityUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class ProfilDao {

    @PersistenceContext
    private EntityManager entityManager;

    public ProfilDto save(ProfilDto profilDto) {
        Profil profil = DtoEntityUtil.profilDtoToProfil(profilDto);
        entityManager.persist(profil);
        profilDto.setId(profil.getId());
        return profilDto;
    }

    public Profil findById(Long id) {
        return entityManager.find(Profil.class, id);
    }

    public Optional<Profil> findByPlayerId(String playerId) {
        return entityManager
                .createQuery("SELECT p FROM Profil p WHERE p.playerId = :playerId", Profil.class)
                .setParameter("playerId", playerId)
                .getResultStream()
                .findFirst();
    }

    public boolean existsByPlayerId(String playerId) {
        return findByPlayerId(playerId).isPresent();
    }
}
