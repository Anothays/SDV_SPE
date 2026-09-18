package com.nebula.rolemanager.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nebula.rolemanager.application.CreateProfilUseCase;
import com.nebula.rolemanager.application.ProfilService;
import com.nebula.rolemanager.domain.port.out.EventPublisherPort;
import com.nebula.rolemanager.domain.port.out.ProfilPort;

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
