package com.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.ProfilDto;
import com.example.kafka.ProfilEventProducer;

@Service
public class ProfilService {

    private final ProfilDao profilDao;
    private final ProfilEventProducer profilEventProducer;

    public ProfilService(ProfilDao profilDao, ProfilEventProducer profilEventProducer) {
        this.profilDao = profilDao;
        this.profilEventProducer = profilEventProducer;
    }

    @Transactional
    public ProfilDto saveProfil(ProfilDto profilDto) {
        ProfilDto saved = profilDao.save(profilDto);
        profilEventProducer.publishProfilCreated(saved);
        return saved;
    }
}
