package com.nebula.rolemanager.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nebula.rolemanager.application.AssignDefaultRoleUseCase;
import com.nebula.rolemanager.application.ChangeRoleUseCase;
import com.nebula.rolemanager.application.RoleAssignmentQueryService;
import com.nebula.rolemanager.domain.port.out.EventPublisherPort;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;

/**
 * Composition root pour les classes application.* : celles-ci ne portent
 * aucune annotation Spring (règle hexagonale), leur instanciation en bean
 * vit donc ici, côté infrastructure.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public AssignDefaultRoleUseCase assignDefaultRoleUseCase(RoleAssignmentPort roleAssignmentPort,
            EventPublisherPort eventPublisherPort) {
        return new AssignDefaultRoleUseCase(roleAssignmentPort, eventPublisherPort);
    }

    @Bean
    public ChangeRoleUseCase changeRoleUseCase(RoleAssignmentPort roleAssignmentPort,
            EventPublisherPort eventPublisherPort) {
        return new ChangeRoleUseCase(roleAssignmentPort, eventPublisherPort);
    }

    @Bean
    public RoleAssignmentQueryService roleAssignmentQueryService(RoleAssignmentPort roleAssignmentPort) {
        return new RoleAssignmentQueryService(roleAssignmentPort);
    }
}
