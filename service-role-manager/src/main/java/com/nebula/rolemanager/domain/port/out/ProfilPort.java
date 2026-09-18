package com.nebula.rolemanager.domain.port.out;

import java.util.Optional;

import com.nebula.rolemanager.domain.Profil;

public interface ProfilPort {

    Profil save(Profil profil);

    Optional<Profil> findByPlayerId(String playerId);

    boolean existsByPlayerId(String playerId);
}
