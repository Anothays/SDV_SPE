package com.nebula.rolemanager.infrastructure.adapter.out.outbox;

import org.springframework.stereotype.Component;

import com.nebula.rolemanager.domain.OutboxEventToPublish;
import com.nebula.rolemanager.domain.port.out.EventPublisherPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(OutboxEventToPublish event) {
        outboxEventRepository.save(OutboxEvent.of(
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                toJson(event.payload())));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de sérialiser le payload de l'événement outbox", e);
        }
    }
}
