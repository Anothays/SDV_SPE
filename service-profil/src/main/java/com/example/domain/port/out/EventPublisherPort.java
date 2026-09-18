package com.example.domain.port.out;

import com.example.domain.OutboxEventToPublish;

public interface EventPublisherPort {

    void publish(OutboxEventToPublish event);
}
