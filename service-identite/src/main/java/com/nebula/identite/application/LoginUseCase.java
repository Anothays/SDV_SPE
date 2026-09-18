package com.nebula.identite.application;

import com.nebula.identite.application.dto.AuthResponse;
import com.nebula.identite.application.dto.LoginRequest;
import com.nebula.identite.domain.Account;
import com.nebula.identite.domain.port.out.AccountPort;
import com.nebula.identite.domain.port.out.PasswordHasherPort;
import com.nebula.identite.domain.port.out.TokenPort;
import com.nebula.identite.exception.InvalidCredentialsException;

public class LoginUseCase {

    private final AccountPort accountPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenPort tokenPort;

    public LoginUseCase(AccountPort accountPort, PasswordHasherPort passwordHasherPort, TokenPort tokenPort) {
        this.accountPort = accountPort;
        this.passwordHasherPort = passwordHasherPort;
        this.tokenPort = tokenPort;
    }

    public AuthResponse execute(LoginRequest request) {
        Account account = accountPort.findByUsername(request.username())
                .filter(a -> passwordHasherPort.matches(request.password(), a.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new AuthResponse(account.getId(),
                tokenPort.issue(account.getId(), account.getUsername(), account.getRole()));
    }
}
