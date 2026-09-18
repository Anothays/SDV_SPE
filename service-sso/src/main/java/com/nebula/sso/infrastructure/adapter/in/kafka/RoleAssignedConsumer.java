package com.nebula.sso.infrastructure.adapter.in.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.sso.application.ApplyRoleAssignmentUseCase;
import com.nebula.sso.application.event.RoleAssignedEvent;
import com.nebula.sso.infrastructure.config.KafkaTopicConfig;

@Component
public class RoleAssignedConsumer {

    private static final Logger log = LoggerFactory.getLogger(RoleAssignedConsumer.class);

    private final ApplyRoleAssignmentUseCase applyRoleAssignmentUseCase;

    public RoleAssignedConsumer(ApplyRoleAssignmentUseCase applyRoleAssignmentUseCase) {
        this.applyRoleAssignmentUseCase = applyRoleAssignmentUseCase;
    }

    @KafkaListener(topics = KafkaTopicConfig.ACCESS_ROLE_ASSIGNED_TOPIC,
            groupId = "sso",
            containerFactory = "roleAssignedKafkaListenerContainerFactory")
    @Transactional
    public void onRoleAssigned(RoleAssignedEvent event) {
        log.info("Événement access.role.assigned reçu : eventId={}, playerId={}, role={}",
                event.eventId(), event.playerId(), event.role());
        applyRoleAssignmentUseCase.execute(event);
    }
}
