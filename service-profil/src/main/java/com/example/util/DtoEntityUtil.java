package com.example.util;

import com.example.dto.ProfilDto;
import com.example.entity.Profil;

public class DtoEntityUtil {

    public static Profil profilDtoToProfil(ProfilDto profilDto) {
        Profil profil = new Profil();
        profil.setPlayerId(profilDto.getPlayerId());
        profil.setUsername(profilDto.getUsername());
        profil.setRegion(profilDto.getRegion());
        profil.setLevel(profilDto.getLevel());
        return profil;
    }

    public static ProfilDto profilToProfilDto(Profil profil) {
        ProfilDto dto = new ProfilDto();
        dto.setId(profil.getId());
        dto.setPlayerId(profil.getPlayerId());
        dto.setUsername(profil.getUsername());
        dto.setRegion(profil.getRegion());
        dto.setLevel(profil.getLevel());
        return dto;
    }
}
