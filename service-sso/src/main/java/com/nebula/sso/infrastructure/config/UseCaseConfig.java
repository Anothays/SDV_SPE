package com.nebula.sso.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nebula.sso.application.ApplyRoleAssignmentUseCase;
import com.nebula.sso.application.LoginUseCase;
import com.nebula.sso.application.RegisterUseCase;
import com.nebula.sso.domain.port.out.AccountPort;
import com.nebula.sso.domain.port.out.EventPublisherPort;
import com.nebula.sso.domain.port.out.PasswordHasherPort;
import com.nebula.sso.domain.port.out.TokenPort;

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

    @Bean
    public ApplyRoleAssignmentUseCase applyRoleAssignmentUseCase(AccountPort accountPort) {
        return new ApplyRoleAssignmentUseCase(accountPort);
    }
}
