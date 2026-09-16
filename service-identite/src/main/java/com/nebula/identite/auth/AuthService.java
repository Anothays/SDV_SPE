package com.nebula.identite.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.identite.auth.dto.AuthResponse;
import com.nebula.identite.auth.dto.LoginRequest;
import com.nebula.identite.auth.dto.RegisterRequest;
import com.nebula.identite.kafka.PlayerRegisteredEvent;
import com.nebula.identite.outbox.OutboxEvent;
import com.nebula.identite.outbox.OutboxEventDao;

@Service
public class AuthService {

    private static final String PLAYERS_REGISTERED_TOPIC = "players.registered";

    private final AccountDao accountDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OutboxEventDao outboxEventDao;
    private final ObjectMapper objectMapper;

    public AuthService(AccountDao accountDao, PasswordEncoder passwordEncoder,
            JwtService jwtService, OutboxEventDao outboxEventDao, ObjectMapper objectMapper) {
        this.accountDao = accountDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.outboxEventDao = outboxEventDao;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (accountDao.existsByUsername(request.username()) || accountDao.existsByEmail(request.email())) {
            throw new DuplicateAccountException();
        }
        Account account = new Account();
        account.setUsername(request.username());
        account.setEmail(request.email());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole("PLAYER");
        account.setRegion(request.region());
        Account saved = accountDao.save(account);

        // Outbox pattern : l'événement est inséré dans la même transaction que le
        // compte, Debezium (CDC sur le WAL Postgres) le publie ensuite vers Kafka.
        // Atomicité garantie, plus de dual-write (spec §11).
        outboxEventDao.save(OutboxEvent.of(
                PLAYERS_REGISTERED_TOPIC,
                saved.getId(),
                "PlayerRegistered",
                toJson(PlayerRegisteredEvent.from(saved))));

        return new AuthResponse(saved.getId(),
                jwtService.issue(saved.getId(), saved.getUsername(), saved.getRole()));
    }

    public AuthResponse login(LoginRequest request) {
        Account account = accountDao.findByUsername(request.username())
                .filter(a -> passwordEncoder.matches(request.password(), a.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new AuthResponse(account.getId(),
                jwtService.issue(account.getId(), account.getUsername(), account.getRole()));
    }

    private String toJson(PlayerRegisteredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de sérialiser PlayerRegisteredEvent", e);
        }
    }
}
