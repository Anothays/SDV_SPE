package com.nebula.rolemanager.application;

import com.nebula.rolemanager.domain.Profil;
import com.nebula.rolemanager.domain.port.out.ProfilPort;
import com.nebula.rolemanager.application.dto.ProfilDto;
import com.nebula.rolemanager.exception.ProfilNotFoundException;

public class ProfilService {

    private final ProfilPort profilPort;

    public ProfilService(ProfilPort profilPort) {
        this.profilPort = profilPort;
    }

    public ProfilDto findByPlayerId(String playerId) {
        return profilPort.findByPlayerId(playerId)
                .map(ProfilMapper::profilToProfilDto)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
    }

    public ProfilDto updateRegion(String playerId, String region) {
        Profil profil = profilPort.findByPlayerId(playerId)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
        profil.setRegion(region);
        Profil updated = profilPort.save(profil);
        return ProfilMapper.profilToProfilDto(updated);
    }
}
