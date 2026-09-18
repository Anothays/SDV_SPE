package com.nebula.identite.infrastructure.adapter.out.outbox;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purge les lignes outbox déjà capturées par Debezium depuis longtemps.
 * Sans risque pour le connecteur : les DELETE sont ignorés par l'EventRouter
 * (delete.handling.mode=drop par défaut), aucun message n'est publié pour eux.
 */
@Component
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);
    private static final int RETENTION_DAYS = 7;

    private final OutboxEventRepository outboxEventRepository;

    public OutboxCleanupJob(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldEvents() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        long deleted = outboxEventRepository.deleteByTimestampBefore(cutoff);
        if (deleted > 0) {
            log.info("Purge outbox : {} événement(s) de plus de {} jours supprimé(s)", deleted, RETENTION_DAYS);
        }
    }
}
