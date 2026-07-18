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

import com.nebula.identite.auth.dto.AuthResponse;
import com.nebula.identite.auth.dto.LoginRequest;
import com.nebula.identite.auth.dto.RegisterRequest;
import com.nebula.identite.kafka.PlayerRegisteredEvent;
import com.nebula.identite.kafka.PlayerRegisteredProducer;

class AuthServiceTest {

    private AccountDao accountDao;
    private JwtService jwtService;
    private PlayerRegisteredProducer producer;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        accountDao = mock(AccountDao.class);
        jwtService = mock(JwtService.class);
        producer = mock(PlayerRegisteredProducer.class);
        when(jwtService.issue(any(), any(), any())).thenReturn("jwt-token");
        authService = new AuthService(accountDao, passwordEncoder, jwtService, producer);
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
    void registerPublishesPlayerRegisteredEvent() {
        when(accountDao.existsByUsername("alice")).thenReturn(false);
        when(accountDao.existsByEmail("alice@example.com")).thenReturn(false);
        when(accountDao.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId("uuid-1");
            return a;
        });

        authService.register(new RegisterRequest("alice", "alice@example.com", "password123", "EU"));

        ArgumentCaptor<PlayerRegisteredEvent> captor = ArgumentCaptor.forClass(PlayerRegisteredEvent.class);
        org.mockito.Mockito.verify(producer).publish(captor.capture());
        assertThat(captor.getValue().playerId()).isEqualTo("uuid-1");
        assertThat(captor.getValue().username()).isEqualTo("alice");
    }
}
