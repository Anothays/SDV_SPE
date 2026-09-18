package com.nebula.identite.application;

import java.time.Instant;
import java.util.UUID;

import com.nebula.identite.application.dto.AuthResponse;
import com.nebula.identite.application.dto.RegisterRequest;
import com.nebula.identite.application.event.PlayerRegisteredEvent;
import com.nebula.identite.domain.Account;
import com.nebula.identite.domain.OutboxEventToPublish;
import com.nebula.identite.domain.port.out.AccountPort;
import com.nebula.identite.domain.port.out.EventPublisherPort;
import com.nebula.identite.domain.port.out.PasswordHasherPort;
import com.nebula.identite.domain.port.out.TokenPort;
import com.nebula.identite.exception.DuplicateAccountException;

public class RegisterUseCase {

    private static final String PLAYERS_REGISTERED_TOPIC = "players.registered";

    private final AccountPort accountPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenPort tokenPort;
    private final EventPublisherPort eventPublisherPort;

    public RegisterUseCase(AccountPort accountPort, PasswordHasherPort passwordHasherPort,
            TokenPort tokenPort, EventPublisherPort eventPublisherPort) {
        this.accountPort = accountPort;
        this.passwordHasherPort = passwordHasherPort;
        this.tokenPort = tokenPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    public AuthResponse execute(RegisterRequest request) {
        if (accountPort.existsByUsername(request.username()) || accountPort.existsByEmail(request.email())) {
            throw new DuplicateAccountException();
        }

        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setUsername(request.username());
        account.setEmail(request.email());
        account.setPasswordHash(passwordHasherPort.hash(request.password()));
        account.setRole("PLAYER");
        account.setRegion(request.region());
        account.setCreatedAt(Instant.now());
        Account saved = accountPort.save(account);

        // Outbox pattern : l'événement est inséré dans la même transaction que le
        // compte (portée par l'adapter d'entrée, Task 13), Debezium (CDC sur le WAL
        // Postgres) le publie ensuite vers Kafka. Atomicité garantie, plus de
        // dual-write (spec §11).
        eventPublisherPort.publish(new OutboxEventToPublish(
                PLAYERS_REGISTERED_TOPIC,
                saved.getId(),
                "PlayerRegistered",
                PlayerRegisteredEvent.from(saved)));

        return new AuthResponse(saved.getId(),
                tokenPort.issue(saved.getId(), saved.getUsername(), saved.getRole()));
    }
}
