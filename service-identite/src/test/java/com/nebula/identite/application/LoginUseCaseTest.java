package com.nebula.identite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nebula.identite.application.dto.AuthResponse;
import com.nebula.identite.application.dto.LoginRequest;
import com.nebula.identite.domain.Account;
import com.nebula.identite.domain.port.out.AccountPort;
import com.nebula.identite.domain.port.out.PasswordHasherPort;
import com.nebula.identite.domain.port.out.TokenPort;
import com.nebula.identite.exception.InvalidCredentialsException;

class LoginUseCaseTest {

    private AccountPort accountPort;
    private PasswordHasherPort passwordHasherPort;
    private TokenPort tokenPort;
    private LoginUseCase loginUseCase;

    @BeforeEach
    void setUp() {
        accountPort = mock(AccountPort.class);
        passwordHasherPort = mock(PasswordHasherPort.class);
        tokenPort = mock(TokenPort.class);
        when(tokenPort.issue(any(), any(), any())).thenReturn("jwt-token");
        loginUseCase = new LoginUseCase(accountPort, passwordHasherPort, tokenPort);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        Account account = new Account();
        account.setId("uuid-1");
        account.setUsername("alice");
        account.setRole("PLAYER");
        account.setPasswordHash("hashed-password123");
        when(accountPort.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordHasherPort.matches("password123", "hashed-password123")).thenReturn(true);

        AuthResponse response = loginUseCase.execute(new LoginRequest("alice", "password123"));

        assertThat(response.playerId()).isEqualTo("uuid-1");
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void loginRejectsWrongPassword() {
        Account account = new Account();
        account.setPasswordHash("hashed-password123");
        when(accountPort.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordHasherPort.matches("wrong", "hashed-password123")).thenReturn(false);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownUsername() {
        when(accountPort.findByUsername("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequest("inconnu", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
