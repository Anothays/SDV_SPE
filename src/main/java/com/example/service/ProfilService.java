package com.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.ProfilDto;

@Service
public class ProfilService {

    private final ProfilDao profilDao;

    public ProfilService(ProfilDao profilDao) {
        this.profilDao = profilDao;
    }

    @Transactional
    public ProfilDto saveProfil(ProfilDto profilDto) {
        return profilDao.save(profilDto);
    }
}

