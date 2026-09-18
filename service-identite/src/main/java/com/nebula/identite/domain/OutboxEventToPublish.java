package com.nebula.identite.domain;

public record OutboxEventToPublish(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payload
) {
}
