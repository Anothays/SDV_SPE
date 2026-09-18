package com.nebula.rolemanager.domain.port.out;

import com.nebula.rolemanager.domain.OutboxEventToPublish;

public interface EventPublisherPort {

    void publish(OutboxEventToPublish event);
}
