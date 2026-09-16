package com.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.ProfilDto;
import com.example.entity.Profil;
import com.example.util.DtoEntityUtil;

@Service
public class ProfilService {

    private final ProfilDao profilDao;

    public ProfilService(ProfilDao profilDao) {
        this.profilDao = profilDao;
    }

    @Transactional(readOnly = true)
    public ProfilDto findByPlayerId(String playerId) {
        return profilDao.findByPlayerId(playerId)
                .map(DtoEntityUtil::profilToProfilDto)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
    }

    @Transactional
    public ProfilDto updateRegion(String playerId, String region) {
        Profil profil = profilDao.findByPlayerId(playerId)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
        profil.setRegion(region);
        return DtoEntityUtil.profilToProfilDto(profil);
    }
}
