package com.nebula.rolemanager.infrastructure.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.nebula.rolemanager.domain.Profil;
import com.nebula.rolemanager.domain.port.out.ProfilPort;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class ProfilJpaAdapter implements ProfilPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Profil save(Profil profil) {
        ProfilEntity entity = toEntity(profil);
        if (profil.getId() == null) {
            entityManager.persist(entity);
        } else {
            entity = entityManager.merge(entity);
        }
        return toDomain(entity);
    }

    @Override
    public Optional<Profil> findByPlayerId(String playerId) {
        // getResultList() (et non getResultStream()) : cet adapter est appelé
        // hors transaction (ex. lecture depuis un autre thread) — un stream JPA a
        // besoin d'une connexion ouverte pendant toute sa consommation, une
        // liste est matérialisée immédiatement.
        return entityManager
                .createQuery("SELECT p FROM ProfilEntity p WHERE p.playerId = :playerId", ProfilEntity.class)
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(ProfilJpaAdapter::toDomain);
    }

    @Override
    public boolean existsByPlayerId(String playerId) {
        return findByPlayerId(playerId).isPresent();
    }

    private static ProfilEntity toEntity(Profil profil) {
        ProfilEntity entity = new ProfilEntity();
        entity.setId(profil.getId());
        entity.setPlayerId(profil.getPlayerId());
        entity.setUsername(profil.getUsername());
        entity.setRegion(profil.getRegion());
        entity.setLevel(profil.getLevel());
        // Merge sur update : préserver createdAt existant pour ne pas l'écraser à null.
        entity.setCreatedAt(profil.getCreatedAt());
        return entity;
    }

    private static Profil toDomain(ProfilEntity entity) {
        Profil profil = new Profil();
        profil.setId(entity.getId());
        profil.setPlayerId(entity.getPlayerId());
        profil.setUsername(entity.getUsername());
        profil.setRegion(entity.getRegion());
        profil.setLevel(entity.getLevel());
        profil.setCreatedAt(entity.getCreatedAt());
        profil.setUpdatedAt(entity.getUpdatedAt());
        return profil;
    }
}
