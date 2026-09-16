package com.example.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ligne de la table outbox lue par le connecteur Debezium (pattern Outbox
 * Event Router) : chaque insertion, dans la même transaction que l'entité
 * métier, est transformée en message Kafka — topic = aggregatetype,
 * clé = aggregateid, valeur = payload brut. Aucun code applicatif ne publie
 * directement sur Kafka (spec §11, dual-write corrigé).
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String aggregatetype;

    @Column(nullable = false)
    private String aggregateid;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    // "timestamp" est un mot réservé MySQL : colonne renommée en base.
    @Column(name = "occurred_at", nullable = false)
    private Instant timestamp;

    protected OutboxEvent() {
    }

    public static OutboxEvent of(String aggregatetype, String aggregateid, String type, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID().toString();
        event.aggregatetype = aggregatetype;
        event.aggregateid = aggregateid;
        event.type = type;
        event.payload = payload;
        event.timestamp = Instant.now();
        return event;
    }

    public String getId() { return id; }
    public String getAggregatetype() { return aggregatetype; }
    public String getAggregateid() { return aggregateid; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getTimestamp() { return timestamp; }
}
