package com.example.dao;

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
    	
    	Profil profil = DtoEntityUtil.profilDToToProfil(profilDto);
    	
    	entityManager.persist(profil);
    	
    	profilDto.setId(profil.getId());
    	
    	return profilDto;
        
    }
    
    public Profil findById(Long id) {
        return entityManager.find(Profil.class, id);
    }
    
}
