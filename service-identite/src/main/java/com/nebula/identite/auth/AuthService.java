package com.nebula.identite.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.identite.auth.dto.AuthResponse;
import com.nebula.identite.auth.dto.LoginRequest;
import com.nebula.identite.auth.dto.RegisterRequest;
import com.nebula.identite.kafka.PlayerRegisteredEvent;
import com.nebula.identite.kafka.PlayerRegisteredProducer;

@Service
public class AuthService {

    private final AccountDao accountDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PlayerRegisteredProducer playerRegisteredProducer;

    public AuthService(AccountDao accountDao, PasswordEncoder passwordEncoder,
            JwtService jwtService, PlayerRegisteredProducer playerRegisteredProducer) {
        this.accountDao = accountDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.playerRegisteredProducer = playerRegisteredProducer;
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
        playerRegisteredProducer.publish(PlayerRegisteredEvent.from(saved));
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
}
