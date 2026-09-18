package com.nebula.sso.domain.port.out;

import com.nebula.sso.domain.OutboxEventToPublish;

public interface EventPublisherPort {

    void publish(OutboxEventToPublish event);
}
