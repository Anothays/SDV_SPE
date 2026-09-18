package com.example.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.CreateProfilUseCase;
import com.example.application.ProfilService;
import com.example.domain.port.out.EventPublisherPort;
import com.example.domain.port.out.ProfilPort;

/**
 * Composition root pour les classes application.* : celles-ci ne portent
 * aucune annotation Spring (règle hexagonale), leur instanciation en bean
 * vit donc ici, côté infrastructure.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public ProfilService profilService(ProfilPort profilPort) {
        return new ProfilService(profilPort);
    }

    @Bean
    public CreateProfilUseCase createProfilUseCase(ProfilPort profilPort, EventPublisherPort eventPublisherPort) {
        return new CreateProfilUseCase(profilPort, eventPublisherPort);
    }
}
