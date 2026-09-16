package com.example.outbox;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventDao extends JpaRepository<OutboxEvent, String> {

    long deleteByTimestampBefore(Instant cutoff);
}
