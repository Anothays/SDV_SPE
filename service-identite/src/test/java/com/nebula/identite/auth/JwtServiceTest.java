package com.nebula.identite.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

class JwtServiceTest {

    @Test
    void issuedTokenCarriesClaimsAndVerifiesWithPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        JwtService jwtService = new JwtService((RSAPrivateKey) keyPair.getPrivate());

        String token = jwtService.issue("player-123", "alice", "PLAYER");

        // Vérification avec la clé publique uniquement : c'est le contrat
        // qu'utiliseront la gateway et les autres services.
        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("player-123");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("PLAYER");
        assertThat(claims.getExpiration()).isAfter(new Date());
    }
}
