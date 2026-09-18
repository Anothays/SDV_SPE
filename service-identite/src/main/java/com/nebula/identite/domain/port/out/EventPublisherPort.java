package com.nebula.identite.domain.port.out;

import com.nebula.identite.domain.OutboxEventToPublish;

public interface EventPublisherPort {

    void publish(OutboxEventToPublish event);
}
