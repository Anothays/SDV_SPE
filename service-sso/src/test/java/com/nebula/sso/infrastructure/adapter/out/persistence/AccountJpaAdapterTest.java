package com.nebula.sso.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.sso.domain.Account;
import com.nebula.sso.domain.port.out.AccountPort;

// URL H2 dédiée (distincte du "sso" partagé par SsoApplicationTests/
// AuthControllerIT) : cette classe est un @SpringBootTest "nu", donc Spring
// réutilise le même contexte mis en cache que SsoApplicationTests. Si
// AuthControllerIT (@DirtiesContext) s'exécute entre les deux, son
// EntityManagerFactory fait un DROP sur la base H2 nommée partagée
// (DB_CLOSE_DELAY=-1) au moment de sa fermeture — ce qui vide aussi les tables
// du contexte caché, jamais recréées puisqu'aucun nouveau contexte ne boote.
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:sso-account-adapter;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
class AccountJpaAdapterTest {

    @Autowired
    private AccountPort accountPort;

    @Test
    void savesAssignedAccount() {
        // id/createdAt sont désormais générés côté domaine (RegisterUseCase),
        // pas par l'adapter : ce test les fournit lui-même, comme le ferait
        // l'appelant réel.
        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setUsername("alice");
        account.setEmail("alice@example.com");
        account.setPasswordHash("hash");
        account.setRole("PLAYER");
        account.setRegion("EU");
        account.setCreatedAt(Instant.now());

        Account saved = accountPort.save(account);

        assertThat(saved.getId()).isEqualTo(account.getId());
        assertThat(saved.getCreatedAt()).isEqualTo(account.getCreatedAt());
        assertThat(accountPort.existsByUsername("alice")).isTrue();
        assertThat(accountPort.existsByEmail("alice@example.com")).isTrue();
        assertThat(accountPort.findByUsername("alice"))
                .hasValueSatisfying(a -> assertThat(a.getEmail()).isEqualTo("alice@example.com"));
    }

    @Test
    void existsByUsernameIsFalseForUnknownUser() {
        assertThat(accountPort.existsByUsername("inconnu")).isFalse();
    }
}
