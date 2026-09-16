package com.nebula.identite.service;

import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Date;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    private static final Duration VALIDITY = Duration.ofHours(1);

    private final RSAPrivateKey privateKey;

    public JwtService(RSAPrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Émet un JWT RS256. Seul le service Identité détient la clé privée :
     * gateway et services valident avec la clé publique, sans secret partagé.
     */
    public String issue(String playerId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(playerId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + VALIDITY.toMillis()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
