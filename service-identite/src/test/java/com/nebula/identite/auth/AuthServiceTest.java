package com.nebula.identite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.identite.auth.dto.AuthResponse;
import com.nebula.identite.auth.dto.LoginRequest;
import com.nebula.identite.auth.dto.RegisterRequest;
import com.nebula.identite.outbox.OutboxEvent;
import com.nebula.identite.outbox.OutboxEventDao;

class AuthServiceTest {

    private AccountDao accountDao;
    private JwtService jwtService;
    private OutboxEventDao outboxEventDao;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        accountDao = mock(AccountDao.class);
        jwtService = mock(JwtService.class);
        outboxEventDao = mock(OutboxEventDao.class);
        when(jwtService.issue(any(), any(), any())).thenReturn("jwt-token");
        authService = new AuthService(accountDao, passwordEncoder, jwtService, outboxEventDao, objectMapper);
    }

    @Test
    void registerHashesPasswordAndReturnsTokenWithPlayerId() {
        when(accountDao.existsByUsername("alice")).thenReturn(false);
        when(accountDao.existsByEmail("alice@example.com")).thenReturn(false);
        when(accountDao.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId("uuid-1"); // simule le @PrePersist
            return a;
        });

        AuthResponse response = authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123", "EU"));

        assertThat(response.playerId()).isEqualTo("uuid-1");
        assertThat(response.token()).isEqualTo("jwt-token");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        org.mockito.Mockito.verify(accountDao).save(captor.capture());
        Account saved = captor.getValue();
        // Jamais de mot de passe en clair en base
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo("PLAYER");
        assertThat(saved.getRegion()).isEqualTo("EU");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(accountDao.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "new@example.com", "password123", "EU")))
                .isInstanceOf(DuplicateAccountException.class);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        Account account = new Account();
        account.setId("uuid-1");
        account.setUsername("alice");
        account.setRole("PLAYER");
        account.setPasswordHash(passwordEncoder.encode("password123"));
        when(accountDao.findByUsername("alice")).thenReturn(Optional.of(account));

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.playerId()).isEqualTo("uuid-1");
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void loginRejectsWrongPassword() {
        Account account = new Account();
        account.setPasswordHash(passwordEncoder.encode("password123"));
        when(accountDao.findByUsername("alice")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void registerWritesPlayerRegisteredEventToOutbox() {
        when(accountDao.existsByUsername("alice")).thenReturn(false);
        when(accountDao.existsByEmail("alice@example.com")).thenReturn(false);
        when(accountDao.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId("uuid-1");
            return a;
        });

        authService.register(new RegisterRequest("alice", "alice@example.com", "password123", "EU"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        org.mockito.Mockito.verify(outboxEventDao).save(captor.capture());
        OutboxEvent event = captor.getValue();
        // topic = aggregatetype, clé Kafka = aggregateid (playerId) : contrat du
        // connecteur Debezium EventRouter, ne pas renommer sans mettre à jour la
        // config du connecteur (service-messaging/debezium/identite-outbox-connector.json)
        assertThat(event.getAggregatetype()).isEqualTo("players.registered");
        assertThat(event.getAggregateid()).isEqualTo("uuid-1");
        assertThat(event.getPayload()).contains("\"playerId\":\"uuid-1\"", "\"username\":\"alice\"");
        // Jamais de PII (email) dans l'événement publié
        assertThat(event.getPayload()).doesNotContain("alice@example.com");
    }
}
