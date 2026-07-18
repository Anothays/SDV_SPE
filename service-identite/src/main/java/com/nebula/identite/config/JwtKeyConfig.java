package com.nebula.identite.config;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class JwtKeyConfig {

    /**
     * Charge la clé privée RS256 depuis un PEM (PKCS#8).
     * L'emplacement est configurable pour pouvoir monter une vraie clé en prod.
     */
    @Bean
    public RSAPrivateKey jwtPrivateKey(
            @Value("${jwt.private-key-location}") Resource privateKeyPem) throws Exception {
        String pem = new String(privateKeyPem.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
