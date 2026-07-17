package com.example.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.TelemetryEventDto;
import com.example.kafka.TelemetryEventProducer;

import jakarta.validation.Valid;

/**
 * Ingestion de télémétrie : fire-and-forget.
 * Aucun traitement synchrone, aucune écriture en base — l'événement part
 * sur Kafka et la requête répond immédiatement 202 Accepted.
 */
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryEventProducer telemetryEventProducer;

    public TelemetryController(TelemetryEventProducer telemetryEventProducer) {
        this.telemetryEventProducer = telemetryEventProducer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ingest(@Valid @RequestBody TelemetryEventDto event) {
        telemetryEventProducer.publishPlayerAction(event);
    }
}
