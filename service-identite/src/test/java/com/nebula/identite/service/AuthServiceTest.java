package com.nebula.identite.service;

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
import com.nebula.identite.dto.AuthResponse;
import com.nebula.identite.dto.LoginRequest;
import com.nebula.identite.dto.RegisterRequest;
import com.nebula.identite.entity.Account;
import com.nebula.identite.exception.DuplicateAccountException;
import com.nebula.identite.exception.InvalidCredentialsException;
import com.nebula.identite.outbox.OutboxEvent;
import com.nebula.identite.outbox.OutboxEventRepository;
import com.nebula.identite.repository.AccountRepository;

class AuthServiceTest {

    private AccountRepository accountRepository;
    private JwtService jwtService;
    private OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        jwtService = mock(JwtService.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        when(jwtService.issue(any(), any(), any())).thenReturn("jwt-token");
        authService = new AuthService(accountRepository, passwordEncoder, jwtService, outboxEventRepository, objectMapper);
    }

    @Test
    void registerHashesPasswordAndReturnsTokenWithPlayerId() {
        when(accountRepository.existsByUsername("alice")).thenReturn(false);
        when(accountRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId("uuid-1"); // simule le @PrePersist
            return a;
        });

        AuthResponse response = authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123", "EU"));

        assertThat(response.playerId()).isEqualTo("uuid-1");
        assertThat(response.token()).isEqualTo("jwt-token");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        org.mockito.Mockito.verify(accountRepository).save(captor.capture());
        Account saved = captor.getValue();
        // Jamais de mot de passe en clair en base
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo("PLAYER");
        assertThat(saved.getRegion()).isEqualTo("EU");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(accountRepository.existsByUsername("alice")).thenReturn(true);

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
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(account));

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.playerId()).isEqualTo("uuid-1");
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void loginRejectsWrongPassword() {
        Account account = new Account();
        account.setPasswordHash(passwordEncoder.encode("password123"));
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void registerWritesPlayerRegisteredEventToOutbox() {
        when(accountRepository.existsByUsername("alice")).thenReturn(false);
        when(accountRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId("uuid-1");
            return a;
        });

        authService.register(new RegisterRequest("alice", "alice@example.com", "password123", "EU"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        org.mockito.Mockito.verify(outboxEventRepository).save(captor.capture());
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
