package com.example.util;

import com.example.dto.ProfilDto;
import com.example.entity.Profil;

public class DtoEntityUtil {
	

    public static Profil profilDToToProfil(ProfilDto profilDto) {
    	
    	Profil profil = new Profil();
    	
    	profil.setName(profilDto.getName());
    	
    	return profil;
    	
    }

}
