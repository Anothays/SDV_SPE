package com.example.domain.port.out;

import java.util.Optional;

import com.example.domain.Profil;

public interface ProfilPort {

    Profil save(Profil profil);

    Optional<Profil> findByPlayerId(String playerId);

    boolean existsByPlayerId(String playerId);
}
