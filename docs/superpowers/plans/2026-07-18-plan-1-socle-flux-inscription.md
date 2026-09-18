# Plan 1 — Socle événementiel & flux inscription

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer le flux inscription de bout en bout : `POST /auth/register` sur un nouveau service Identité → événement `players.registered` sur Kafka → création automatique et idempotente du profil dans le service Profil existant, avec retry + dead letter topic.

**Architecture:** Chorégraphie pure (spec `docs/superpowers/specs/2026-07-18-nebula-architecture-design.md`, §2-§5). Identité (nouveau, Spring Boot + PostgreSQL) publie `players.registered` (event-carried state transfer, sans PII). Profil (existant, Spring Boot + MySQL) consomme, crée le profil en réaction, republie `players.profil.created`. Aucun appel HTTP inter-services.

**Tech Stack:** Java 17, Spring Boot 3.4.1, spring-kafka, JJWT 0.12.6 (RS256), PostgreSQL 16, MySQL 8 (existant), H2 en tests, spring-kafka-test (EmbeddedKafka).

## Global Constraints

- Plan exécuté depuis la racine du repo : `/Users/jeremysananikone/Projets/SUP DE VINCI/12 - SPECIALISATION FULLSTACK/Projet/MaDemo` (chemins relatifs à cette racine).
- **Prérequis avant la Task 1 : demander à l'utilisateur de committer son renommage en cours** (renames stagés `service-*` + `docker-compose.yml` modifié sur la branche `raf`). Ne pas committer à sa place.
- Aucun appel HTTP entre services — toute communication inter-services passe par Kafka.
- Événements JSON avec enveloppe commune : `eventId` (UUID), `eventVersion` (int), `occurredAt` (ISO-8601). Jamais de PII (email, mot de passe) dans un événement.
- Topics : `players.registered` (clé playerId, 3 partitions, rétention 7 j), `players.registered.dlt` (1 partition, 14 j). Producteur JSON **sans type headers** (`spring.json.add.type.headers=false`) : pas de fuite de noms de classes entre services.
- Le contrat d'événement est **dupliqué volontairement** dans chaque service (pas de lib partagée) : la duplication du DTO est le découplage.
- Nouveau service : package `com.nebula.identite`, port 8082. Service Profil existant : package `com.example`, conventions existantes (commentaires en français, DAO via EntityManager).
- Clés RSA de dev committées dans le repo, préfixées `dev-` (projet école, documenté). JWT RS256, expiration 1 h, claims `sub`=playerId, `username`, `role`.
- Commits conventionnels en anglais, un commit par tâche (l'utilisateur a validé ce plan qui inclut les commits).
- Tout `./mvnw` se lance depuis le répertoire du service concerné.

---

### Task 1: Réparer les chemins du docker-compose après renommage

Le renommage `monitoring-service` → `service-monitoring` et `load-testing` → `service-load-testing` a été fait sur les répertoires mais pas dans `docker-compose.yml` : les volumes pointent vers des chemins morts.

**Files:**
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: un `docker compose config` valide, prérequis de toutes les tâches suivantes.

- [x] **Step 1: Vérifier l'état réel**

Run: `docker compose config -q && ls monitoring-service load-testing 2>&1`
Expected: `ls` répond "No such file or directory" pour les deux (les répertoires s'appellent désormais `service-monitoring` et `service-load-testing`). Si `docker-compose.yml` référence encore les anciens noms, continuer ; s'il a déjà été corrigé par l'utilisateur, passer directement à la Task 2.

- [x] **Step 2: Corriger les chemins de volumes**

Dans `docker-compose.yml`, remplacer toutes les occurrences :
- `./monitoring-service/` → `./service-monitoring/` (volumes de `prometheus`, `grafana`, `alertmanager`)
- `./load-testing/` → `./service-load-testing/` (volumes de `jmeter`)

- [x] **Step 3: Valider la config**

Run: `docker compose config -q && echo OK`
Expected: `OK` (aucune erreur).

- [x] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "fix: update compose volume paths after service directory rename"
```

---

### Task 2: Squelette du service Identité

**Files:**
- Create: `service-identite/pom.xml`
- Create: `service-identite/Dockerfile`
- Create: `service-identite/src/main/java/com/nebula/identite/IdentiteApplication.java`
- Create: `service-identite/src/main/resources/application.properties`
- Create: `service-identite/src/test/resources/application.properties`
- Test: `service-identite/src/test/java/com/nebula/identite/IdentiteApplicationTests.java`

**Interfaces:**
- Produces: module Maven `service-identite` compilable et testable (H2 en test), base de tous les ajouts suivants.

- [x] **Step 1: Copier le wrapper Maven depuis service-profil**

```bash
cp -r service-profil/.mvn service-identite/.mvn
cp service-profil/mvnw service-profil/mvnw.cmd service-identite/
```

(Créer d'abord `mkdir -p service-identite`.)

- [x] **Step 2: Écrire le pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.nebula</groupId>
    <artifactId>service-identite</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>service-identite</name>
    <description>Service Identité : comptes, authentification, émission des JWT</description>

    <properties>
        <java.version>17</java.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- JWT RS256 -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [x] **Step 3: Classe application**

`service-identite/src/main/java/com/nebula/identite/IdentiteApplication.java` :

```java
package com.nebula.identite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdentiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentiteApplication.class, args);
    }
}
```

- [x] **Step 4: application.properties (main)**

`service-identite/src/main/resources/application.properties` :

```properties
spring.application.name=service-identite
# 8082 : 8080 est pris par service-profil, 8081 par AKHQ
server.port=8082

# ===============================
# DATASOURCE POSTGRESQL
# ===============================
# En local : container db-identite. En Docker : surcharge par SPRING_DATASOURCE_URL.
spring.datasource.url=jdbc:postgresql://localhost:5432/identite
spring.datasource.username=devuser
spring.datasource.password=devpassword
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update

# ===============================
# METRIQUES
# ===============================
management.endpoints.web.exposure.include=prometheus,health,info
management.metrics.distribution.percentiles-histogram.http.server.requests=true

# ===============================
# KAFKA
# ===============================
# En local : listener EXTERNAL du container Kafka.
# En Docker : surcharge par SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
spring.kafka.bootstrap-servers=localhost:9094
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
# Pas de type headers : le nom de classe Java ne doit pas fuiter vers les consommateurs
spring.kafka.producer.properties.spring.json.add.type.headers=false
# Producteur idempotent + acks=all (spec §6)
spring.kafka.producer.acks=all
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.admin.fail-fast=false

# ===============================
# JWT
# ===============================
jwt.private-key-location=classpath:keys/dev-private.pem
```

- [x] **Step 5: application.properties (test, H2)**

`service-identite/src/test/resources/application.properties` :

```properties
spring.datasource.url=jdbc:h2:mem:identite;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.kafka.bootstrap-servers=localhost:9094
spring.kafka.admin.fail-fast=false
```

- [x] **Step 6: Dockerfile (même pattern que service-profil)**

`service-identite/Dockerfile` :

```dockerfile
# Étape 1 — build avec l'image Maven officielle (JDK 17 inclus)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Télécharge les dépendances en cache séparé (optimisation)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Étape 2 — image finale légère
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [x] **Step 7: Test de contexte**

`service-identite/src/test/java/com/nebula/identite/IdentiteApplicationTests.java` :

```java
package com.nebula.identite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IdentiteApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

Note : ce test échouera tant que la Task 4 (clés JWT) n'existe pas ? Non — aucun bean ne référence encore `jwt.private-key-location`. Il doit passer dès maintenant.

- [x] **Step 8: Vérifier**

Run: `cd service-identite && ./mvnw -q test`
Expected: `BUILD SUCCESS`, 1 test passé.

- [x] **Step 9: Commit**

```bash
git add service-identite
git commit -m "feat(identite): scaffold identity service (Spring Boot, PostgreSQL, Kafka)"
```

---

### Task 3: Émission de JWT RS256 (JwtService + clés)

**Files:**
- Create: `service-identite/scripts/generate-dev-jwt-keys.sh`
- Create: `service-identite/src/main/resources/keys/dev-private.pem` (généré)
- Create: `service-identite/src/main/resources/keys/dev-public.pem` (généré)
- Create: `service-identite/src/main/java/com/nebula/identite/config/JwtKeyConfig.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/JwtService.java`
- Test: `service-identite/src/test/java/com/nebula/identite/auth/JwtServiceTest.java`

**Interfaces:**
- Produces: `JwtService.issue(String playerId, String username, String role) : String` (JWT RS256 signé, exp 1 h) ; bean `RSAPrivateKey` chargé depuis `jwt.private-key-location`.
- La clé publique `dev-public.pem` sera consommée par la gateway (plan 4).

- [x] **Step 1: Écrire le test qui échoue**

`service-identite/src/test/java/com/nebula/identite/auth/JwtServiceTest.java` :

```java
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
```

- [x] **Step 2: Vérifier l'échec**

Run: `cd service-identite && ./mvnw -q test -Dtest=JwtServiceTest`
Expected: FAIL — `cannot find symbol: class JwtService` (erreur de compilation).

- [x] **Step 3: Implémenter JwtService**

`service-identite/src/main/java/com/nebula/identite/auth/JwtService.java` :

```java
package com.nebula.identite.auth;

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
```

- [x] **Step 4: Vérifier que le test passe**

Run: `cd service-identite && ./mvnw -q test -Dtest=JwtServiceTest`
Expected: PASS.

- [x] **Step 5: Script de génération des clés de dev + génération**

`service-identite/scripts/generate-dev-jwt-keys.sh` :

```bash
#!/bin/sh
# Génère la paire de clés RSA de DÉVELOPPEMENT pour la signature des JWT.
# Ces clés sont committées volontairement (projet école) — ne jamais faire ça en prod.
set -eu
KEYS_DIR="$(dirname "$0")/../src/main/resources/keys"
mkdir -p "$KEYS_DIR"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/dev-private.pem"
openssl pkey -in "$KEYS_DIR/dev-private.pem" -pubout -out "$KEYS_DIR/dev-public.pem"
echo "Clés générées dans $KEYS_DIR"
```

Run: `chmod +x service-identite/scripts/generate-dev-jwt-keys.sh && service-identite/scripts/generate-dev-jwt-keys.sh`
Expected: `Clés générées dans …/src/main/resources/keys` ; les deux fichiers `.pem` existent.

- [x] **Step 6: Charger la clé privée au démarrage**

`service-identite/src/main/java/com/nebula/identite/config/JwtKeyConfig.java` :

```java
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
```

- [x] **Step 7: Vérifier la suite complète**

Run: `cd service-identite && ./mvnw -q test`
Expected: `BUILD SUCCESS` — `contextLoads` valide au passage le chargement réel du PEM.

- [x] **Step 8: Commit**

```bash
git add service-identite
git commit -m "feat(identite): issue RS256 JWTs with dev keypair"
```

---

### Task 4: Compte joueur + inscription/connexion (AuthService)

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/auth/Account.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/AccountDao.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/dto/RegisterRequest.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/dto/LoginRequest.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/dto/AuthResponse.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/DuplicateAccountException.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/InvalidCredentialsException.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/AuthService.java`
- Test: `service-identite/src/test/java/com/nebula/identite/auth/AuthServiceTest.java`

**Interfaces:**
- Produces: `AuthService.register(RegisterRequest) : AuthResponse` et `AuthService.login(LoginRequest) : AuthResponse` ; `AuthResponse(String playerId, String token)` ; entité `Account` (id UUID String 36, username/email uniques, passwordHash bcrypt, role, region, createdAt).
- Consumes: `JwtService.issue(playerId, username, role)` (Task 3).
- La Task 5 modifiera `AuthService.register` pour publier `players.registered`.

- [x] **Step 1: Écrire le test qui échoue**

`service-identite/src/test/java/com/nebula/identite/auth/AuthServiceTest.java` :

```java
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

class AuthServiceTest {

    private AccountDao accountDao;
    private JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        accountDao = mock(AccountDao.class);
        jwtService = mock(JwtService.class);
        when(jwtService.issue(any(), any(), any())).thenReturn("jwt-token");
        authService = new AuthService(accountDao, passwordEncoder, jwtService);
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
}
```

- [x] **Step 2: Vérifier l'échec**

Run: `cd service-identite && ./mvnw -q test -Dtest=AuthServiceTest`
Expected: FAIL — classes `Account`, `AccountDao`, `AuthService`… introuvables (compilation).

- [x] **Step 3: Implémenter**

`service-identite/src/main/java/com/nebula/identite/auth/Account.java` :

```java
package com.nebula.identite.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String region;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

`service-identite/src/main/java/com/nebula/identite/auth/AccountDao.java` :

```java
package com.nebula.identite.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDao extends JpaRepository<Account, String> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<Account> findByUsername(String username);
}
```

`service-identite/src/main/java/com/nebula/identite/auth/dto/RegisterRequest.java` :

```java
package com.nebula.identite.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 20) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Pattern(regexp = "EU|NA|ASIA") String region) {
}
```

`service-identite/src/main/java/com/nebula/identite/auth/dto/LoginRequest.java` :

```java
package com.nebula.identite.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
```

`service-identite/src/main/java/com/nebula/identite/auth/dto/AuthResponse.java` :

```java
package com.nebula.identite.auth.dto;

public record AuthResponse(String playerId, String token) {
}
```

`service-identite/src/main/java/com/nebula/identite/auth/DuplicateAccountException.java` :

```java
package com.nebula.identite.auth;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException() {
        super("username ou email déjà utilisé");
    }
}
```

`service-identite/src/main/java/com/nebula/identite/auth/InvalidCredentialsException.java` :

```java
package com.nebula.identite.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("identifiants invalides");
    }
}
```

`service-identite/src/main/java/com/nebula/identite/auth/AuthService.java` :

```java
package com.nebula.identite.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.identite.auth.dto.AuthResponse;
import com.nebula.identite.auth.dto.LoginRequest;
import com.nebula.identite.auth.dto.RegisterRequest;

@Service
public class AuthService {

    private final AccountDao accountDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AccountDao accountDao, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountDao = accountDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
```

- [x] **Step 4: Vérifier que les tests passent**

Run: `cd service-identite && ./mvnw -q test -Dtest=AuthServiceTest`
Expected: PASS (4 tests).

- [x] **Step 5: Commit**

```bash
git add service-identite
git commit -m "feat(identite): account registration and login with bcrypt"
```

---

### Task 5: Publication de l'événement players.registered

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/kafka/KafkaTopicConfig.java`
- Create: `service-identite/src/main/java/com/nebula/identite/kafka/PlayerRegisteredEvent.java`
- Create: `service-identite/src/main/java/com/nebula/identite/kafka/PlayerRegisteredProducer.java`
- Modify: `service-identite/src/main/java/com/nebula/identite/auth/AuthService.java`
- Modify: `service-identite/src/test/java/com/nebula/identite/auth/AuthServiceTest.java`
- Test: `service-identite/src/test/java/com/nebula/identite/kafka/PlayerRegisteredEventTest.java`
- Test: `service-identite/src/test/java/com/nebula/identite/kafka/PlayerRegisteredProducerIT.java`

**Interfaces:**
- Produces: topic `players.registered` (3 partitions, rétention 7 j), payload JSON `{eventId, eventVersion, occurredAt, playerId, username, region}`, clé Kafka = playerId, **sans** header de type. C'est le contrat que le service Profil consommera (Task 8+).
- Consumes: `Account` (Task 4).

- [x] **Step 1: Écrire les tests qui échouent**

`service-identite/src/test/java/com/nebula/identite/kafka/PlayerRegisteredEventTest.java` :

```java
package com.nebula.identite.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.nebula.identite.auth.Account;

class PlayerRegisteredEventTest {

    @Test
    void fromAccountFillsEnvelopeWithoutPii() {
        Account account = new Account();
        account.setId("uuid-1");
        account.setUsername("alice");
        account.setEmail("alice@example.com");
        account.setRegion("EU");

        PlayerRegisteredEvent event = PlayerRegisteredEvent.from(account);

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(Instant.parse(event.occurredAt())).isNotNull(); // ISO-8601 valide
        assertThat(event.playerId()).isEqualTo("uuid-1");
        assertThat(event.username()).isEqualTo("alice");
        assertThat(event.region()).isEqualTo("EU");
        // Contrat spec §4 : l'événement n'a AUCUN champ PII (email, mot de passe)
        assertThat(PlayerRegisteredEvent.class.getRecordComponents())
                .extracting("name")
                .containsExactlyInAnyOrder("eventId", "eventVersion", "occurredAt",
                        "playerId", "username", "region");
    }

    @Test
    void eachEventGetsAFreshEventId() {
        Account account = new Account();
        account.setId("uuid-1");

        assertThat(PlayerRegisteredEvent.from(account).eventId())
                .isNotEqualTo(PlayerRegisteredEvent.from(account).eventId());
    }
}
```

- [x] **Step 2: Vérifier l'échec**

Run: `cd service-identite && ./mvnw -q test -Dtest=PlayerRegisteredEventTest`
Expected: FAIL — `PlayerRegisteredEvent` introuvable.

- [x] **Step 3: Implémenter événement, topic et producteur**

`service-identite/src/main/java/com/nebula/identite/kafka/PlayerRegisteredEvent.java` :

```java
package com.nebula.identite.kafka;

import java.time.Instant;
import java.util.UUID;

import com.nebula.identite.auth.Account;

/**
 * Contrat players.registered v1 (spec §4).
 * Event-carried state transfer : tout ce dont Profil a besoin, rien de plus.
 * Pas de PII : l'email reste confiné au service Identité.
 */
public record PlayerRegisteredEvent(
        String eventId,
        int eventVersion,
        String occurredAt,
        String playerId,
        String username,
        String region) {

    public static PlayerRegisteredEvent from(Account account) {
        return new PlayerRegisteredEvent(
                UUID.randomUUID().toString(),
                1,
                Instant.now().toString(),
                account.getId(),
                account.getUsername(),
                account.getRegion());
    }
}
```

`service-identite/src/main/java/com/nebula/identite/kafka/KafkaTopicConfig.java` :

```java
package com.nebula.identite.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class KafkaTopicConfig {

    public static final String PLAYERS_REGISTERED_TOPIC = "players.registered";

    // Fait métier : clé playerId (ordre garanti par joueur), 3 partitions
    // (parallélisme des consommateurs), rétention 7 jours (spec §4).
    @Bean
    public NewTopic playersRegisteredTopic() {
        return TopicBuilder.name(PLAYERS_REGISTERED_TOPIC)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7 * 24 * 60 * 60 * 1000L))
                .build();
    }
}
```

`service-identite/src/main/java/com/nebula/identite/kafka/PlayerRegisteredProducer.java` (même pattern que `ProfilEventProducer` existant) :

```java
package com.nebula.identite.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlayerRegisteredProducer {

    private static final Logger log = LoggerFactory.getLogger(PlayerRegisteredProducer.class);

    private final KafkaTemplate<String, PlayerRegisteredEvent> kafkaTemplate;

    public PlayerRegisteredProducer(KafkaTemplate<String, PlayerRegisteredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie l'événement d'inscription. Envoi asynchrone : une panne de Kafka
     * ne doit pas faire échouer l'inscription HTTP (limite documentée spec §6,
     * évolution citée : transactional outbox).
     */
    public void publish(PlayerRegisteredEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC, event.playerId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Échec de publication players.registered pour playerId={}",
                                event.playerId(), ex);
                    } else {
                        log.info("Événement players.registered publié : playerId={}, partition={}, offset={}",
                                event.playerId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
```

Modifier `AuthService` — injecter le producteur et publier après la sauvegarde :

```java
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
```

Mettre à jour `AuthServiceTest` : ajouter le mock du producteur et vérifier la publication. Dans `setUp()` :

```java
    private PlayerRegisteredProducer producer;
    // ...
    @BeforeEach
    void setUp() {
        accountDao = mock(AccountDao.class);
        jwtService = mock(JwtService.class);
        producer = mock(PlayerRegisteredProducer.class);
        when(jwtService.issue(any(), any(), any())).thenReturn("jwt-token");
        authService = new AuthService(accountDao, passwordEncoder, jwtService, producer);
    }
```

(imports à ajouter : `com.nebula.identite.kafka.PlayerRegisteredEvent`, `com.nebula.identite.kafka.PlayerRegisteredProducer`)

Et ajouter le test :

```java
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
```

- [x] **Step 4: Vérifier les tests unitaires**

Run: `cd service-identite && ./mvnw -q test -Dtest='PlayerRegisteredEventTest,AuthServiceTest'`
Expected: PASS (7 tests).

- [x] **Step 5: Test d'intégration EmbeddedKafka (contrat sur le fil)**

`service-identite/src/test/java/com/nebula/identite/kafka/PlayerRegisteredProducerIT.java` :

```java
package com.nebula.identite.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PlayerRegisteredProducerIT {

    @Autowired
    private PlayerRegisteredProducer producer;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void publishesJsonKeyedByPlayerIdWithoutTypeHeaders() throws Exception {
        producer.publish(new PlayerRegisteredEvent(
                "evt-1", 1, "2026-07-18T10:00:00Z", "p-1", "alice", "EU"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "true", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC);
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC, Duration.ofSeconds(10));

            // Clé = playerId : garantie d'ordre par joueur
            assertThat(record.key()).isEqualTo("p-1");
            // Pas de header de type : aucun nom de classe Java ne fuite
            assertThat(record.headers().lastHeader("__TypeId__")).isNull();

            JsonNode json = new ObjectMapper().readTree(record.value());
            assertThat(json.get("eventId").asText()).isEqualTo("evt-1");
            assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
            assertThat(json.get("playerId").asText()).isEqualTo("p-1");
            assertThat(json.get("username").asText()).isEqualTo("alice");
            assertThat(json.get("region").asText()).isEqualTo("EU");
            assertThat(json.has("email")).isFalse();
        }
    }
}
```

- [x] **Step 6: Vérifier la suite complète**

Run: `cd service-identite && ./mvnw -q test`
Expected: `BUILD SUCCESS`, tous tests verts (unitaires + IT EmbeddedKafka).

- [x] **Step 7: Commit**

```bash
git add service-identite
git commit -m "feat(identite): publish players.registered event on registration"
```

---

### Task 6: Endpoints REST /auth + sécurité du service Identité

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/auth/AuthController.java`
- Create: `service-identite/src/main/java/com/nebula/identite/auth/ApiExceptionHandler.java`
- Create: `service-identite/src/main/java/com/nebula/identite/config/SecurityConfig.java`
- Test: `service-identite/src/test/java/com/nebula/identite/auth/AuthControllerIT.java`

**Interfaces:**
- Produces: `POST /auth/register` → 201 `{playerId, token}` (409 si doublon, 400 si invalide) ; `POST /auth/login` → 200 `{playerId, token}` (401 si identifiants invalides). Routes que la gateway exposera en public (plan 4).
- Consumes: `AuthService` (Tasks 4-5).

- [x] **Step 1: Écrire le test qui échoue**

`service-identite/src/test/java/com/nebula/identite/auth/AuthControllerIT.java` :

```java
package com.nebula.identite.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nebula.identite.kafka.PlayerRegisteredProducer;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    // Pas de broker Kafka dans ce test : le producteur est neutralisé
    @MockitoBean
    private PlayerRegisteredProducer playerRegisteredProducer;

    private static final String ALICE = """
            {"username":"alice","email":"alice@example.com","password":"password123","region":"EU"}
            """;

    @Test
    void registerReturns201WithPlayerIdAndToken() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(not(emptyString())))
                .andExpect(jsonPath("$.token").value(not(emptyString())));
    }

    @Test
    void registerReturns409OnDuplicate() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isConflict());
    }

    @Test
    void registerReturns400OnInvalidBody() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"pas-un-email\",\"password\":\"court\",\"region\":\"MARS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsTokenThenRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyString())));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [x] **Step 2: Vérifier l'échec**

Run: `cd service-identite && ./mvnw -q test -Dtest=AuthControllerIT`
Expected: FAIL — 401/404 sur les routes (contrôleur et SecurityConfig absents).

- [x] **Step 3: Implémenter contrôleur, handler d'erreurs et sécurité**

`service-identite/src/main/java/com/nebula/identite/auth/AuthController.java` :

```java
package com.nebula.identite.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nebula.identite.auth.dto.AuthResponse;
import com.nebula.identite.auth.dto.LoginRequest;
import com.nebula.identite.auth.dto.RegisterRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
```

`service-identite/src/main/java/com/nebula/identite/auth/ApiExceptionHandler.java` :

```java
package com.nebula.identite.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateAccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDuplicate(DuplicateAccountException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return Map.of("error", ex.getMessage());
    }
}
```

(La validation `@Valid` produit déjà un 400 via Spring, rien à ajouter.)

`service-identite/src/main/java/com/nebula/identite/config/SecurityConfig.java` :

```java
package com.nebula.identite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // API REST stateless : pas de session, pas de CSRF
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [x] **Step 4: Vérifier**

Run: `cd service-identite && ./mvnw -q test`
Expected: `BUILD SUCCESS`, toute la suite verte.

- [x] **Step 5: Commit**

```bash
git add service-identite
git commit -m "feat(identite): REST auth endpoints with stateless security"
```

---

### Task 7: Intégration docker-compose du service Identité

**Files:**
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: services compose `db-identite` (PostgreSQL 16) et `identite` (port hôte 8082), sur le réseau `monitoring-net` existant. L'E2E (Task 12) et les plans suivants en dépendent.

- [x] **Step 1: Ajouter les deux services**

Dans `docker-compose.yml`, ajouter au niveau de `services:` :

```yaml
  db-identite:
    image: postgres:16-alpine
    container_name: "db-identite"
    environment:
      POSTGRES_DB: identite
      POSTGRES_USER: devuser
      POSTGRES_PASSWORD: devpassword
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U devuser -d identite"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks:
      - monitoring-net

  identite:
    container_name: "identite"
    build: service-identite
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db-identite:5432/identite
      SPRING_DATASOURCE_USERNAME: devuser
      SPRING_DATASOURCE_PASSWORD: devpassword
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: prometheus,health,info
    depends_on:
      db-identite:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - monitoring-net
    restart: on-failure
```

- [x] **Step 2: Builder et démarrer**

Run: `docker compose up -d --build identite`
Expected: `db-identite`, `kafka` puis `identite` démarrent. Vérifier : `docker compose ps identite` → `Up`.

- [x] **Step 3: Vérifier l'inscription et l'événement de bout en bout**

Run:

```bash
curl -s -X POST localhost:8082/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"compose-check","email":"compose-check@example.com","password":"password123","region":"EU"}'
```

Expected: `{"playerId":"<uuid>","token":"eyJ..."}`

Run:

```bash
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic players.registered \
  --from-beginning --max-messages 1 --timeout-ms 15000
```

Expected: une ligne JSON contenant `"username":"compose-check"` et aucun champ `email`.

- [x] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(compose): add identite service with dedicated postgres"
```

---

### Task 8: Refonte de l'entité Profil (playerId, username, region, level)

**Files:**
- Modify: `service-profil/src/main/java/com/example/entity/Profil.java`
- Modify: `service-profil/src/main/java/com/example/dto/ProfilDto.java`
- Modify: `service-profil/src/main/java/com/example/util/DtoEntityUtil.java`
- Modify: `service-profil/src/main/java/com/example/dao/ProfilDao.java`
- Modify: `service-profil/src/main/java/com/example/kafka/ProfilEventProducer.java`
- Modify: `service-profil/src/main/java/com/example/kafka/ProfilEventConsumer.java`
- Modify: `service-profil/pom.xml` (dépendances test : h2, spring-security-test, spring-kafka-test)
- Create: `service-profil/src/test/resources/application.properties`
- Delete: `service-profil/src/main/java/com/example/DataTestRunner.java` (stub mort : n'insère rien, imprime un id null)
- Delete: `service-profil/src/test/java/com/example/MaDemo/ProfilServiceTest.java` (test vide, remplacé en Task 9)
- Test: `service-profil/src/test/java/com/example/MaDemo/ProfilDaoTest.java`

**Interfaces:**
- Produces: entité `Profil` conforme au modèle spec §5 (`player_id` UUID String unique, `username`, `region`, `level` défaut 1, timestamps) ; `ProfilDao.findByPlayerId(String) : Optional<Profil>` et `ProfilDao.existsByPlayerId(String) : boolean` ; `ProfilDto {id, playerId, username, region, level}` ; tests exécutables sans MySQL (H2).
- La Task 9 (consommateur) et la Task 11 (contrôleur) reposent sur ces signatures.

- [x] **Step 1: Dépendances test + config H2**

Dans `service-profil/pom.xml`, ajouter aux `<dependencies>` :

```xml
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
```

Créer `service-profil/src/test/resources/application.properties` :

```properties
# Tests autonomes : H2 en mode MySQL, pas de broker Kafka requis
spring.datasource.url=jdbc:h2:mem:profil;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false
spring.kafka.bootstrap-servers=localhost:9094
spring.kafka.listener.auto-startup=false
spring.kafka.admin.fail-fast=false
```

- [x] **Step 2: Écrire le test DAO qui échoue**

`service-profil/src/test/java/com/example/MaDemo/ProfilDaoTest.java` :

```java
package com.example.MaDemo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.ProfilDto;

@SpringBootTest
@Transactional
class ProfilDaoTest {

    @Autowired
    private ProfilDao profilDao;

    @Test
    void savesThenFindsByPlayerId() {
        ProfilDto dto = new ProfilDto();
        dto.setPlayerId("uuid-1");
        dto.setUsername("alice");
        dto.setRegion("EU");
        dto.setLevel(1);

        ProfilDto saved = profilDao.save(dto);

        assertThat(saved.getId()).isNotNull();
        assertThat(profilDao.existsByPlayerId("uuid-1")).isTrue();
        assertThat(profilDao.findByPlayerId("uuid-1"))
                .hasValueSatisfying(p -> {
                    assertThat(p.getUsername()).isEqualTo("alice");
                    assertThat(p.getLevel()).isEqualTo(1);
                    assertThat(p.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void existsByPlayerIdIsFalseForUnknownPlayer()  {
        assertThat(profilDao.existsByPlayerId("inconnu")).isFalse();
    }
}
```

- [x] **Step 3: Vérifier l'échec**

Run: `cd service-profil && ./mvnw -q test -Dtest=ProfilDaoTest`
Expected: FAIL — `setPlayerId`, `findByPlayerId`… introuvables (compilation).

- [x] **Step 4: Implémenter la refonte**

`service-profil/src/main/java/com/example/entity/Profil.java` :

```java
package com.example.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "profil")
public class Profil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiant métier émis par le service Identité (players.registered)
    @Column(name = "player_id", nullable = false, unique = true, length = 36)
    private String playerId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

Note : l'ancienne colonne `nom` reste en base en dev (`ddl-auto=update` n'efface pas) — sans incidence, documenté ici.

`service-profil/src/main/java/com/example/dto/ProfilDto.java` :

```java
package com.example.dto;

public class ProfilDto {

    private Long id;
    private String playerId;
    private String username;
    private String region;
    private int level;

    public ProfilDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
```

`service-profil/src/main/java/com/example/util/DtoEntityUtil.java` :

```java
package com.example.util;

import com.example.dto.ProfilDto;
import com.example.entity.Profil;

public class DtoEntityUtil {

    public static Profil profilDtoToProfil(ProfilDto profilDto) {
        Profil profil = new Profil();
        profil.setPlayerId(profilDto.getPlayerId());
        profil.setUsername(profilDto.getUsername());
        profil.setRegion(profilDto.getRegion());
        profil.setLevel(profilDto.getLevel());
        return profil;
    }

    public static ProfilDto profilToProfilDto(Profil profil) {
        ProfilDto dto = new ProfilDto();
        dto.setId(profil.getId());
        dto.setPlayerId(profil.getPlayerId());
        dto.setUsername(profil.getUsername());
        dto.setRegion(profil.getRegion());
        dto.setLevel(profil.getLevel());
        return dto;
    }
}
```

`service-profil/src/main/java/com/example/dao/ProfilDao.java` (garder le style EntityManager existant) :

```java
package com.example.dao;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.dto.ProfilDto;
import com.example.entity.Profil;
import com.example.util.DtoEntityUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class ProfilDao {

    @PersistenceContext
    private EntityManager entityManager;

    public ProfilDto save(ProfilDto profilDto) {
        Profil profil = DtoEntityUtil.profilDtoToProfil(profilDto);
        entityManager.persist(profil);
        profilDto.setId(profil.getId());
        return profilDto;
    }

    public Profil findById(Long id) {
        return entityManager.find(Profil.class, id);
    }

    public Optional<Profil> findByPlayerId(String playerId) {
        return entityManager
                .createQuery("SELECT p FROM Profil p WHERE p.playerId = :playerId", Profil.class)
                .setParameter("playerId", playerId)
                .getResultStream()
                .findFirst();
    }

    public boolean existsByPlayerId(String playerId) {
        return findByPlayerId(playerId).isPresent();
    }
}
```

`ProfilEventProducer.publishProfilCreated` — la clé devient le playerId (cohérence de partitionnement) et les logs suivent :

```java
    public void publishProfilCreated(ProfilDto profil) {
        kafkaTemplate.send(KafkaTopicConfig.PROFIL_CREATED_TOPIC, profil.getPlayerId(), profil)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Échec de publication de l'événement players.profil.created pour playerId={}",
                                profil.getPlayerId(), ex);
                    } else {
                        log.info("Événement players.profil.created publié : playerId={}, partition={}, offset={}",
                                profil.getPlayerId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
```

`ProfilEventConsumer.onProfilCreated` — adapter le log :

```java
    @KafkaListener(topics = KafkaTopicConfig.PROFIL_CREATED_TOPIC, groupId = "mademo")
    public void onProfilCreated(ProfilDto profil) {
        log.info("Événement players.profil.created reçu : playerId={}, username={}",
                profil.getPlayerId(), profil.getUsername());
    }
```

Supprimer `service-profil/src/main/java/com/example/DataTestRunner.java` et
`service-profil/src/test/java/com/example/MaDemo/ProfilServiceTest.java`.

`ProfilService.saveProfil` et `ProfilController.create` référencent encore l'ancien monde mais compilent toujours (ils passent par `ProfilDto`) — ils seront remaniés en Tasks 9 et 11.

- [x] **Step 5: Vérifier**

Run: `cd service-profil && ./mvnw -q test`
Expected: `BUILD SUCCESS` — `ProfilDaoTest` (2 tests) + `MaDemoApplicationTests` passent désormais sans MySQL ni Kafka.

- [x] **Step 6: Commit**

```bash
git add service-profil
git commit -m "refactor(profil): align Profil entity with spec model (playerId, region, level)"
```

---

### Task 9: Consommation de players.registered → création idempotente du profil

**Files:**
- Create: `service-profil/src/main/java/com/example/dto/PlayerRegisteredEvent.java`
- Create: `service-profil/src/main/java/com/example/service/ProfilCreationService.java`
- Create: `service-profil/src/main/java/com/example/kafka/KafkaConsumerConfig.java`
- Create: `service-profil/src/main/java/com/example/kafka/PlayerRegisteredConsumer.java`
- Modify: `service-profil/src/main/java/com/example/kafka/KafkaTopicConfig.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/ProfilCreationServiceTest.java`

**Interfaces:**
- Consumes: contrat `players.registered` v1 (Task 5) — DTO **dupliqué volontairement** côté Profil ; `ProfilDao` (Task 8) ; `ProfilEventProducer` (existant).
- Produces: `ProfilCreationService.onPlayerRegistered(PlayerRegisteredEvent) : void` (idempotent, `IllegalArgumentException` si playerId manquant) ; consumer group `profil-service` ; factory `playerRegisteredKafkaListenerContainerFactory` (la Task 10 y branchera le DLT).

- [x] **Step 1: Écrire le test qui échoue**

`service-profil/src/test/java/com/example/MaDemo/ProfilCreationServiceTest.java` :

```java
package com.example.MaDemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.dao.ProfilDao;
import com.example.dto.PlayerRegisteredEvent;
import com.example.dto.ProfilDto;
import com.example.kafka.ProfilEventProducer;
import com.example.service.ProfilCreationService;

class ProfilCreationServiceTest {

    private ProfilDao profilDao;
    private ProfilEventProducer profilEventProducer;
    private ProfilCreationService profilCreationService;

    private static final PlayerRegisteredEvent EVENT = new PlayerRegisteredEvent(
            "evt-1", 1, "2026-07-18T10:00:00Z", "uuid-1", "alice", "EU");

    @BeforeEach
    void setUp() {
        profilDao = mock(ProfilDao.class);
        profilEventProducer = mock(ProfilEventProducer.class);
        profilCreationService = new ProfilCreationService(profilDao, profilEventProducer);
    }

    @Test
    void createsProfilAtLevelOneAndPublishesProfilCreated() {
        when(profilDao.existsByPlayerId("uuid-1")).thenReturn(false);
        when(profilDao.save(any(ProfilDto.class))).thenAnswer(inv -> inv.getArgument(0));

        profilCreationService.onPlayerRegistered(EVENT);

        ArgumentCaptor<ProfilDto> captor = ArgumentCaptor.forClass(ProfilDto.class);
        verify(profilDao).save(captor.capture());
        ProfilDto saved = captor.getValue();
        assertThat(saved.getPlayerId()).isEqualTo("uuid-1");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getRegion()).isEqualTo("EU");
        assertThat(saved.getLevel()).isEqualTo(1);
        verify(profilEventProducer).publishProfilCreated(saved);
    }

    @Test
    void skipsWhenProfilAlreadyExists_idempotence() {
        when(profilDao.existsByPlayerId("uuid-1")).thenReturn(true);

        profilCreationService.onPlayerRegistered(EVENT);

        verify(profilDao, never()).save(any());
        verify(profilEventProducer, never()).publishProfilCreated(any());
    }

    @Test
    void rejectsEventWithoutPlayerId() {
        PlayerRegisteredEvent invalide = new PlayerRegisteredEvent(
                "evt-2", 1, "2026-07-18T10:00:00Z", null, "bob", "EU");

        assertThatThrownBy(() -> profilCreationService.onPlayerRegistered(invalide))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [x] **Step 2: Vérifier l'échec**

Run: `cd service-profil && ./mvnw -q test -Dtest=ProfilCreationServiceTest`
Expected: FAIL — `PlayerRegisteredEvent`, `ProfilCreationService` introuvables.

- [x] **Step 3: Implémenter**

`service-profil/src/main/java/com/example/dto/PlayerRegisteredEvent.java` :

```java
package com.example.dto;

/**
 * Contrat players.registered v1, côté consommateur.
 * Dupliqué volontairement depuis service-identite (pas de lib partagée) :
 * chaque service possède sa copie du contrat, c'est le découplage.
 */
public record PlayerRegisteredEvent(
        String eventId,
        int eventVersion,
        String occurredAt,
        String playerId,
        String username,
        String region) {
}
```

`service-profil/src/main/java/com/example/service/ProfilCreationService.java` :

```java
package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.PlayerRegisteredEvent;
import com.example.dto.ProfilDto;
import com.example.kafka.ProfilEventProducer;

@Service
public class ProfilCreationService {

    private static final Logger log = LoggerFactory.getLogger(ProfilCreationService.class);

    private final ProfilDao profilDao;
    private final ProfilEventProducer profilEventProducer;

    public ProfilCreationService(ProfilDao profilDao, ProfilEventProducer profilEventProducer) {
        this.profilDao = profilDao;
        this.profilEventProducer = profilEventProducer;
    }

    /**
     * Crée le profil en réaction à players.registered.
     * Idempotent : Kafka garantit at-least-once, un rejeu ne doit rien créer.
     */
    @Transactional
    public void onPlayerRegistered(PlayerRegisteredEvent event) {
        if (event.playerId() == null || event.playerId().isBlank()) {
            throw new IllegalArgumentException("playerId manquant dans players.registered");
        }
        if (profilDao.existsByPlayerId(event.playerId())) {
            log.info("Profil déjà existant pour playerId={}, événement ignoré (idempotence)",
                    event.playerId());
            return;
        }
        ProfilDto dto = new ProfilDto();
        dto.setPlayerId(event.playerId());
        dto.setUsername(event.username());
        dto.setRegion(event.region());
        dto.setLevel(1);
        ProfilDto saved = profilDao.save(dto);
        profilEventProducer.publishProfilCreated(saved);
    }
}
```

Dans `service-profil/src/main/java/com/example/kafka/KafkaTopicConfig.java`, ajouter la constante (pas de bean : le topic appartient au producteur Identité) :

```java
    public static final String PLAYERS_REGISTERED_TOPIC = "players.registered";
```

`service-profil/src/main/java/com/example/kafka/KafkaConsumerConfig.java` :

```java
package com.example.kafka;

import java.util.Map;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.example.dto.PlayerRegisteredEvent;

@Configuration
public class KafkaConsumerConfig {

    /**
     * Factory dédiée à players.registered : désérialise vers le DTO local
     * en ignorant les type headers (le producteur n'en émet pas — aucune
     * classe Java partagée entre services).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>
            playerRegisteredKafkaListenerContainerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        JsonDeserializer<PlayerRegisteredEvent> valueDeserializer =
                new JsonDeserializer<>(PlayerRegisteredEvent.class, false);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), valueDeserializer));
        return factory;
    }
}
```

`service-profil/src/main/java/com/example/kafka/PlayerRegisteredConsumer.java` :

```java
package com.example.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.dto.PlayerRegisteredEvent;
import com.example.service.ProfilCreationService;

@Component
public class PlayerRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlayerRegisteredConsumer.class);

    private final ProfilCreationService profilCreationService;

    public PlayerRegisteredConsumer(ProfilCreationService profilCreationService) {
        this.profilCreationService = profilCreationService;
    }

    @KafkaListener(topics = KafkaTopicConfig.PLAYERS_REGISTERED_TOPIC,
            groupId = "profil-service",
            containerFactory = "playerRegisteredKafkaListenerContainerFactory")
    public void onPlayerRegistered(PlayerRegisteredEvent event) {
        log.info("Événement players.registered reçu : eventId={}, playerId={}",
                event.eventId(), event.playerId());
        profilCreationService.onPlayerRegistered(event);
    }
}
```

- [x] **Step 4: Vérifier**

Run: `cd service-profil && ./mvnw -q test`
Expected: `BUILD SUCCESS` — `ProfilCreationServiceTest` (3 tests) et le reste au vert.

- [x] **Step 5: Commit**

```bash
git add service-profil
git commit -m "feat(profil): create profile reactively from players.registered event"
```

---

### Task 10: Retry + dead letter topic sur le consommateur Profil

**Files:**
- Modify: `service-profil/src/main/java/com/example/kafka/KafkaConsumerConfig.java`
- Modify: `service-profil/src/main/java/com/example/kafka/KafkaTopicConfig.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/PlayerRegisteredFlowIT.java`

**Interfaces:**
- Consumes: factory `playerRegisteredKafkaListenerContainerFactory` (Task 9), `KafkaTemplate` auto-configuré.
- Produces: topic `players.registered.dlt` (1 partition, rétention 14 j) recevant les événements en échec après 3 retries (spec §6). Le monitoring (plan 5) alertera sur ce topic.

- [x] **Step 1: Écrire le test d'intégration qui échoue**

`service-profil/src/test/java/com/example/MaDemo/PlayerRegisteredFlowIT.java` :

```java
package com.example.MaDemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.example.dao.ProfilDao;
import com.example.dto.PlayerRegisteredEvent;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 3,
        topics = {"players.registered", "players.registered.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
class PlayerRegisteredFlowIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProfilDao profilDao;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void validEventCreatesProfil() {
        kafkaTemplate.send("players.registered", "uuid-ok", new PlayerRegisteredEvent(
                "evt-ok", 1, "2026-07-18T10:00:00Z", "uuid-ok", "alice", "EU"));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(profilDao.existsByPlayerId("uuid-ok")).isTrue());
    }

    @Test
    void invalidEventLandsInDeadLetterTopicAfterRetries() {
        kafkaTemplate.send("players.registered", "sans-id", new PlayerRegisteredEvent(
                "evt-ko", 1, "2026-07-18T10:00:00Z", null, "bob", "EU"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-probe", "true", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "players.registered.dlt");
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, "players.registered.dlt", Duration.ofSeconds(20));
            assertThat(record.value()).contains("\"eventId\":\"evt-ko\"");
        }
    }
}
```

- [x] **Step 2: Vérifier l'échec**

Run: `cd service-profil && ./mvnw -q test -Dtest=PlayerRegisteredFlowIT`
Expected: `validEventCreatesProfil` PASS (listener déjà branché), mais `invalidEventLandsInDeadLetterTopicAfterRetries` FAIL — timeout : rien n'est publié sur le DLT (pas encore d'error handler).

- [x] **Step 3: Brancher retry + DLT**

Dans `service-profil/src/main/java/com/example/kafka/KafkaTopicConfig.java`, ajouter le bean du DLT (le DLT appartient au consommateur qui échoue) :

```java
    public static final String PLAYERS_REGISTERED_DLT_TOPIC = "players.registered.dlt";

    // Dead letter : faible volume, rétention longue pour investigation (spec §4)
    @Bean
    public NewTopic playersRegisteredDltTopic() {
        return TopicBuilder.name(PLAYERS_REGISTERED_DLT_TOPIC)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(14 * 24 * 60 * 60 * 1000L))
                .build();
    }
```

Dans `KafkaConsumerConfig`, brancher l'error handler sur la factory :

```java
package com.example.kafka;

import java.util.Map;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import com.example.dto.PlayerRegisteredEvent;

@Configuration
public class KafkaConsumerConfig {

    /**
     * Factory dédiée à players.registered : désérialise vers le DTO local
     * en ignorant les type headers, et route vers le DLT après épuisement
     * des retries (at-least-once, spec §6).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>
            playerRegisteredKafkaListenerContainerFactory(
                    KafkaProperties kafkaProperties,
                    KafkaTemplate<String, Object> kafkaTemplate) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        JsonDeserializer<PlayerRegisteredEvent> valueDeserializer =
                new JsonDeserializer<>(PlayerRegisteredEvent.class, false);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, PlayerRegisteredEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), valueDeserializer));

        // Le DLT n'a qu'une partition : on n'hérite pas de la partition d'origine
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopicConfig.PLAYERS_REGISTERED_DLT_TOPIC, 0));

        // 3 retries avec backoff exponentiel, puis DLT (spec §6)
        var backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxAttempts(3);
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, backOff));
        return factory;
    }
}
```

- [x] **Step 4: Vérifier que les deux tests passent**

Run: `cd service-profil && ./mvnw -q test -Dtest=PlayerRegisteredFlowIT`
Expected: PASS (2 tests) — le rejet arrive dans `players.registered.dlt` après les retries.

- [x] **Step 5: Suite complète**

Run: `cd service-profil && ./mvnw -q test`
Expected: `BUILD SUCCESS`.

- [x] **Step 6: Commit**

```bash
git add service-profil
git commit -m "feat(profil): retry with exponential backoff and dead letter topic"
```

---

### Task 11: API REST Profil en lecture/mise à jour (fin du POST de création)

**Files:**
- Modify: `service-profil/src/main/java/com/example/controller/ProfilController.java`
- Modify: `service-profil/src/main/java/com/example/service/ProfilService.java`
- Create: `service-profil/src/main/java/com/example/dto/UpdateProfilRequest.java`
- Create: `service-profil/src/main/java/com/example/service/ProfilNotFoundException.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/ProfilControllerIT.java`

**Interfaces:**
- Produces: `GET /api/profils/{playerId}` → 200 `ProfilDto` / 404 ; `PUT /api/profils/{playerId}` → 200 (seule la `region` est modifiable par le joueur). La création par POST disparaît : un profil naît uniquement par événement (spec §5).
- Consumes: `ProfilDao.findByPlayerId` (Task 8).
- Note : l'authentification reste le HTTP Basic existant (`ali`/`password123`) jusqu'à la gateway JWT (plan 4).

- [x] **Step 1: Écrire le test qui échoue**

`service-profil/src/test/java/com/example/MaDemo/ProfilControllerIT.java` :

```java
package com.example.MaDemo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.ProfilDto;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfilControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfilDao profilDao;

    @BeforeEach
    void seedProfil() {
        ProfilDto dto = new ProfilDto();
        dto.setPlayerId("uuid-1");
        dto.setUsername("alice");
        dto.setRegion("EU");
        dto.setLevel(1);
        profilDao.save(dto);
    }

    @Test
    void getReturnsProfilByPlayerId() throws Exception {
        mockMvc.perform(get("/api/profils/uuid-1").with(httpBasic("ali", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.level").value(1));
    }

    @Test
    void getReturns404ForUnknownPlayer() throws Exception {
        mockMvc.perform(get("/api/profils/inconnu").with(httpBasic("ali", "password123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void putUpdatesRegionOnly() throws Exception {
        mockMvc.perform(put("/api/profils/uuid-1").with(httpBasic("ali", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"NA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("NA"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void postCreateIsGone() throws Exception {
        mockMvc.perform(post("/api/profils").with(httpBasic("ali", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"direct\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void isProtectedByAuthentication() throws Exception {
        mockMvc.perform(get("/api/profils/uuid-1"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [x] **Step 2: Vérifier l'échec**

Run: `cd service-profil && ./mvnw -q test -Dtest=ProfilControllerIT`
Expected: FAIL — GET/PUT inexistants (404/405), POST encore présent.

- [x] **Step 3: Implémenter**

`service-profil/src/main/java/com/example/service/ProfilNotFoundException.java` :

```java
package com.example.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProfilNotFoundException extends RuntimeException {

    public ProfilNotFoundException(String playerId) {
        super("profil introuvable pour playerId=" + playerId);
    }
}
```

`service-profil/src/main/java/com/example/dto/UpdateProfilRequest.java` :

```java
package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Seule la région est modifiable par le joueur (spec §5). */
public record UpdateProfilRequest(
        @NotBlank @Pattern(regexp = "EU|NA|ASIA") String region) {
}
```

`service-profil/src/main/java/com/example/service/ProfilService.java` — remplacer `saveProfil` (la création vit désormais dans `ProfilCreationService`) :

```java
package com.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dao.ProfilDao;
import com.example.dto.ProfilDto;
import com.example.entity.Profil;
import com.example.util.DtoEntityUtil;

@Service
public class ProfilService {

    private final ProfilDao profilDao;

    public ProfilService(ProfilDao profilDao) {
        this.profilDao = profilDao;
    }

    @Transactional(readOnly = true)
    public ProfilDto findByPlayerId(String playerId) {
        return profilDao.findByPlayerId(playerId)
                .map(DtoEntityUtil::profilToProfilDto)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
    }

    @Transactional
    public ProfilDto updateRegion(String playerId, String region) {
        Profil profil = profilDao.findByPlayerId(playerId)
                .orElseThrow(() -> new ProfilNotFoundException(playerId));
        profil.setRegion(region);
        return DtoEntityUtil.profilToProfilDto(profil);
    }
}
```

`service-profil/src/main/java/com/example/controller/ProfilController.java` :

```java
package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.ProfilDto;
import com.example.dto.UpdateProfilRequest;
import com.example.service.ProfilService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profils")
public class ProfilController {

    private final ProfilService profilService;

    public ProfilController(ProfilService profilService) {
        this.profilService = profilService;
    }

    // La création n'est plus exposée : un profil naît par événement
    // players.registered (spec §5), jamais par POST direct.

    @GetMapping("/{playerId}")
    public ProfilDto get(@PathVariable String playerId) {
        return profilService.findByPlayerId(playerId);
    }

    @PutMapping("/{playerId}")
    public ProfilDto update(@PathVariable String playerId,
            @Valid @RequestBody UpdateProfilRequest request) {
        return profilService.updateRegion(playerId, request.region());
    }
}
```

Note : `postCreateIsGone` attend 405 (méthode absente sur une route existante).

- [x] **Step 4: Vérifier**

Run: `cd service-profil && ./mvnw -q test`
Expected: `BUILD SUCCESS`, toute la suite verte.

- [x] **Step 5: Commit**

```bash
git add service-profil
git commit -m "feat(profil): read/update REST API, profile creation is event-only"
```

---

### Task 12: Vérification E2E du flux inscription + documentation

**Files:**
- Modify: `README.md` (section architecture + commandes de vérification)

**Interfaces:**
- Consumes: tout le plan.
- Produces: procédure de démo reproductible (soutenance) ; point de départ du plan 2 (flux match).

- [x] **Step 1: Tout reconstruire et démarrer**

Run: `docker compose up -d --build`
Expected: `app`, `db`, `identite`, `db-identite`, `kafka`, `prometheus`, `grafana`, `alertmanager`, `influxdb`, `akhq` tous `Up` (`docker compose ps`).

- [x] **Step 2: Dérouler le flux complet**

```bash
# 1. Inscription → récupérer le playerId
PLAYER_ID=$(curl -s -X POST localhost:8082/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"e2e-alice","email":"e2e-alice@example.com","password":"password123","region":"EU"}' \
  | sed -E 's/.*"playerId":"([^"]+)".*/\1/')
echo "playerId=$PLAYER_ID"

# 2. Laisser la chorégraphie se dérouler
sleep 5

# 3. Le profil a été créé en réaction, sans aucun appel HTTP entre services
curl -s -u ali:password123 "localhost:8080/api/profils/$PLAYER_ID"
```

Expected: le dernier `curl` renvoie `{"id":…,"playerId":"<uuid>","username":"e2e-alice","region":"EU","level":1}`.

- [x] **Step 3: Vérifier l'aval Kafka**

```bash
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic players.profil.created \
  --from-beginning --max-messages 1 --timeout-ms 15000
```

Expected: un JSON avec `"username":"e2e-alice"` (le profil republié). Vérifier aussi dans AKHQ (http://localhost:8081) que `players.registered.dlt` est vide.

- [x] **Step 4: Documenter dans le README**

Ajouter au `README.md` une section :

```markdown
## Flux inscription (plan 1)

Inscription → `players.registered` → création du profil en réaction (chorégraphie,
aucun appel HTTP inter-services). Détails : `docs/superpowers/specs/2026-07-18-nebula-architecture-design.md`.

- Service Identité : http://localhost:8082 (`POST /auth/register`, `POST /auth/login`)
- Service Profil : http://localhost:8080 (`GET/PUT /api/profils/{playerId}`)
- Démo rapide : voir `docs/superpowers/plans/2026-07-18-plan-1-socle-flux-inscription.md`, Task 12.
```

- [x] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: document registration flow and demo procedure"
```

---

## Self-Review (fait à la rédaction)

- **Couverture spec (périmètre plan 1)** : flux inscription §3 ✓ (Tasks 5-9), contrat `players.registered` §4 ✓ (Tasks 5, 9), Identité §5 ✓ (Tasks 2-7), Profil §5 ✓ (Tasks 8-11), retry/DLT §6 ✓ (Task 10), pas de PII §7 ✓ (Tasks 5, 9), bcrypt/JWT RS256 §7 ✓ (Tasks 3-4). Gateway, matchmaking, classement, économie, monitoring : plans 2-5.
- **Placeholders** : aucun — chaque étape code porte son code complet.
- **Cohérence de types** : `PlayerRegisteredEvent` identique (champs et ordre) côté producteur (Task 5) et consommateur (Task 9) ; `AuthService` final défini en Task 5 et consommé tel quel en Task 6 ; `ProfilDao.findByPlayerId/existsByPlayerId` définis Task 8, consommés Tasks 9-11.
