package com.nebula.identite.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nebula.identite.application.LoginUseCase;
import com.nebula.identite.application.RegisterUseCase;
import com.nebula.identite.domain.port.out.AccountPort;
import com.nebula.identite.domain.port.out.EventPublisherPort;
import com.nebula.identite.domain.port.out.PasswordHasherPort;
import com.nebula.identite.domain.port.out.TokenPort;

/**
 * Composition root pour les classes application.* : celles-ci ne portent
 * aucune annotation Spring (règle hexagonale), leur instanciation en bean
 * vit donc ici, côté infrastructure.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public RegisterUseCase registerUseCase(AccountPort accountPort, PasswordHasherPort passwordHasherPort,
            TokenPort tokenPort, EventPublisherPort eventPublisherPort) {
        return new RegisterUseCase(accountPort, passwordHasherPort, tokenPort, eventPublisherPort);
    }

    @Bean
    public LoginUseCase loginUseCase(AccountPort accountPort, PasswordHasherPort passwordHasherPort,
            TokenPort tokenPort) {
        return new LoginUseCase(accountPort, passwordHasherPort, tokenPort);
    }
}
