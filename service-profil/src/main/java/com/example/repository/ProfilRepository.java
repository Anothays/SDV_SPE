package com.example.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.dto.ProfilDto;
import com.example.entity.Profil;
import com.example.mapper.ProfilMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class ProfilRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public ProfilDto save(ProfilDto profilDto) {
        Profil profil = ProfilMapper.profilDtoToProfil(profilDto);
        entityManager.persist(profil);
        profilDto.setId(profil.getId());
        return profilDto;
    }

    public Profil findById(Long id) {
        return entityManager.find(Profil.class, id);
    }

    public Optional<Profil> findByPlayerId(String playerId) {
        // getResultList() (et non getResultStream()) : ce repository est appelé
        // hors transaction (ex. lecture depuis un autre thread) — un stream JPA a
        // besoin d'une connexion ouverte pendant toute sa consommation, une
        // liste est matérialisée immédiatement.
        return entityManager
                .createQuery("SELECT p FROM Profil p WHERE p.playerId = :playerId", Profil.class)
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    public boolean existsByPlayerId(String playerId) {
        return findByPlayerId(playerId).isPresent();
    }
}
