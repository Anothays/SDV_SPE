package com.nebula.rolemanager.domain;

public record OutboxEventToPublish(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payload
) {
}
