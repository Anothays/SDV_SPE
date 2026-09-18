package com.nebula.identite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nebula.identite.application.dto.AuthResponse;
import com.nebula.identite.application.dto.RegisterRequest;
import com.nebula.identite.domain.Account;
import com.nebula.identite.domain.OutboxEventToPublish;
import com.nebula.identite.domain.port.out.AccountPort;
import com.nebula.identite.domain.port.out.EventPublisherPort;
import com.nebula.identite.domain.port.out.PasswordHasherPort;
import com.nebula.identite.domain.port.out.TokenPort;
import com.nebula.identite.exception.DuplicateAccountException;

class RegisterUseCaseTest {

    private AccountPort accountPort;
    private PasswordHasherPort passwordHasherPort;
    private TokenPort tokenPort;
    private EventPublisherPort eventPublisherPort;
    private RegisterUseCase registerUseCase;

    @BeforeEach
    void setUp() {
        accountPort = mock(AccountPort.class);
        passwordHasherPort = mock(PasswordHasherPort.class);
        tokenPort = mock(TokenPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        when(accountPort.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHasherPort.hash("password123")).thenReturn("hashed-password123");
        when(tokenPort.issue(any(), any(), any())).thenReturn("jwt-token");
        registerUseCase = new RegisterUseCase(accountPort, passwordHasherPort, tokenPort, eventPublisherPort);
    }

    @Test
    void registerHashesPasswordAndReturnsTokenWithPlayerId() {
        AuthResponse response = registerUseCase.execute(
                new RegisterRequest("alice", "alice@example.com", "password123", "EU"));

        assertThat(response.playerId()).isNotBlank();
        assertThat(response.token()).isEqualTo("jwt-token");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountPort).save(captor.capture());
        Account saved = captor.getValue();
        // Jamais de mot de passe en clair en base
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password123");
        assertThat(saved.getRole()).isEqualTo("PLAYER");
        assertThat(saved.getRegion()).isEqualTo("EU");
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(accountPort.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> registerUseCase.execute(
                new RegisterRequest("alice", "new@example.com", "password123", "EU")))
                .isInstanceOf(DuplicateAccountException.class);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(accountPort.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registerUseCase.execute(
                new RegisterRequest("bob", "alice@example.com", "password123", "EU")))
                .isInstanceOf(DuplicateAccountException.class);
    }

    @Test
    void registerPublishesPlayerRegisteredEventWithoutPii() {
        registerUseCase.execute(new RegisterRequest("alice", "alice@example.com", "password123", "EU"));

        ArgumentCaptor<OutboxEventToPublish> captor = ArgumentCaptor.forClass(OutboxEventToPublish.class);
        verify(eventPublisherPort).publish(captor.capture());
        OutboxEventToPublish published = captor.getValue();
        // topic = aggregateType, clé Kafka = aggregateId (playerId) : contrat du
        // connecteur Debezium EventRouter, ne pas renommer sans mettre à jour la
        // config du connecteur (service-messaging/debezium/identite-outbox-connector.json)
        assertThat(published.aggregateType()).isEqualTo("players.registered");
        assertThat(published.eventType()).isEqualTo("PlayerRegistered");
        assertThat(published.payload()).isNotNull();
    }
}
