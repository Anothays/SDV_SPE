package com.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.ProfilDto;
import com.example.entity.Profil;
import com.example.exception.ProfilNotFoundException;
import com.example.mapper.ProfilMapper;
import com.example.repository.ProfilRepository;

@Service
public class ProfilService {

    private final ProfilRepository profilRepository;

    public ProfilService(ProfilRepository profilRepository) {
        this.profilRepository = profilRepository;
    }

    @Transactional(readOnly = true)
    public ProfilDto findByPlayerId(String playerId) {
        return profilRepository.findByPlayerId(playerId)
                .map(ProfilMapper::profilToProfilDto)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
    }

    @Transactional
    public ProfilDto updateRegion(String playerId, String region) {
        Profil profil = profilRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
        profil.setRegion(region);
        return ProfilMapper.profilToProfilDto(profil);
    }
}
