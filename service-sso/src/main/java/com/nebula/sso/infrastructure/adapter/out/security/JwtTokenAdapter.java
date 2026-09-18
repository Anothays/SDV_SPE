package com.nebula.sso.infrastructure.adapter.out.security;

import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.nebula.sso.domain.port.out.TokenPort;

import io.jsonwebtoken.Jwts;

@Service
public class JwtTokenAdapter implements TokenPort {

    private static final Duration VALIDITY = Duration.ofHours(1);

    private final RSAPrivateKey privateKey;

    public JwtTokenAdapter(RSAPrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Émet un JWT RS256. Seul le service SSO détient la clé privée :
     * gateway et services valident avec la clé publique, sans secret partagé.
     */
    @Override
    public String issue(String subject, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + VALIDITY.toMillis()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
