# Plan 2 — Migration hexagonale service-profil & service-identite

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire passer `service-profil` puis `service-identite` de
`controller → service → repository/entity` à une architecture hexagonale
(`domain` / `application` / `infrastructure.adapter.{in,out}`), conforme à
`.claude/rules/hexagonal-architecture.md`, sans changer de comportement
observable (mêmes endpoints REST, mêmes contrats Kafka, même atomicité
outbox).

**Architecture:** Spec `docs/superpowers/specs/2026-09-18-hexagonal-migration.md`.
`service-profil` (pilote, `ProfilRepository` déjà une classe manuelle sur
`EntityManager`) migré en premier ; le pattern validé est répliqué sur
`service-identite`. Chaque agrégat JPA (`Profil`, `Account`) obtient un
modèle domaine POJO séparé de son entité. Toute dépendance framework
interdite en `domain`/`application` (Spring, JPA, Kafka, Jackson,
`PasswordEncoder`) devient un `domain.port.out.XxxPort`, implémenté par un
adapter en `infrastructure.adapter.out`. Pas de `domain.port.in` — les use
cases sont des classes simples dans `application`. `@Transactional` se
déplace de l'ex-service vers l'adapter d'entrée (`AuthController`,
`PlayerRegisteredConsumer`).

**Tech Stack:** Java 17, Spring Boot 3.4.1, spring-data-jpa, spring-kafka,
JJWT 0.12.6, Jackson, MySQL 8, H2 (tests), spring-kafka-test (EmbeddedKafka),
JUnit 5 + Mockito.

## Global Constraints

- Plan exécuté depuis la racine du repo : `C:\Users\jeremysananikone\projets\SDV_SPE`.
- Migration interne pure : aucun changement de contrat REST ou d'événement
  Kafka, aucune nouvelle fonctionnalité.
- `domain`/`application` : jamais d'import `org.springframework.*`,
  `jakarta.persistence.*`/`javax.persistence.*`, `org.apache.kafka.*`,
  `com.fasterxml.jackson.*`. **`hexagonal-boundary-guard` est une
  vérification bloquante en fin de toute tâche touchant du code Java —
  ne pas cocher la case tant qu'il ne répond pas "Aucun import interdit
  détecté".** `spring-java-reviewer` en complément (non bloquant, mais ses
  findings bloquants au sens de son propre rapport doivent être corrigés
  avant de committer).
- Pas de `domain.port.in.XxxUseCase` : use cases = classes simples dans
  `application`, cohérent avec `service-profil/CLAUDE.md` et
  `service-identite/CLAUDE.md`.
- `@Transactional` interdit sur les classes `application.*` : il vit sur
  l'adapter d'entrée qui déclenche le use case.
- Tests `service-profil` : le module de test suit un préfixe de package
  hérité `com.example.MaDemo.<sous-package>` (différent du prod
  `com.example.*`) — ne pas le corriger hors périmètre, les nouveaux
  tests suivent la même convention que leurs voisins de couche.
- Contrat outbox inchangé : écriture de l'entité métier + de la ligne
  `outbox_event` dans **la même transaction**, aucune modification du
  connecteur Debezium ni du format `payload` JSON déjà consommé.
- Commits conventionnels en anglais, un commit par tâche.
- `./mvnw test` se lance depuis le répertoire du service concerné
  (`mvn -pl service-profil test` / `mvn -pl service-identite test` depuis
  la racine fonctionnent aussi).
- À la fin de chaque tâche listée dans une phase "Files: Delete", vérifier
  qu'aucune référence résiduelle (import, injection) ne subsiste vers la
  classe supprimée avant de committer.

---

## Phase A — service-profil (pilote)

### Task 1: Domaine `Profil` + `ProfilPort`

**Files:**
- Create: `service-profil/src/main/java/com/example/domain/Profil.java`
- Create: `service-profil/src/main/java/com/example/domain/port/out/ProfilPort.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/domain/ProfilTest.java`

**Interfaces:**
- Produces: `domain.Profil` (POJO : `id`, `playerId`, `username`, `region`,
  `createdAt` — champs actuels de l'entité `entity/Profil.java`, sans
  aucune annotation JPA).
- Produces: `ProfilPort.save(Profil):Profil`,
  `findByPlayerId(String):Optional<Profil>`,
  `existsByPlayerId(String):boolean` (le port n'expose jamais l'entité
  JPA ; `findById(Long)` de l'ancien `ProfilRepository`, non utilisée
  ailleurs dans le code, n'est pas reportée).

- [x] **Step 1: Lire l'entité et le repository actuels**

Lire `service-profil/src/main/java/com/example/entity/Profil.java` et
`service-profil/src/main/java/com/example/repository/ProfilRepository.java`
pour reprendre exactement les mêmes champs/types dans le modèle domaine.

- [x] **Step 2: Test qui échoue**

Écrire `ProfilTest` (construction, égalité/accesseurs sur les champs
repris de l'entité). Run `mvn -pl service-profil test -Dtest=ProfilTest`
→ échoue (classe absente).

- [x] **Step 3: Implémenter `domain.Profil` et `ProfilPort`**

POJO simple (constructeur + getters, pas de setters si l'entité actuelle
est immuable côté lecture ; sinon reprendre le même style que l'entité).
Interface `ProfilPort` avec les 3 méthodes ci-dessus, aucune dépendance
autre que `java.util.Optional` et `domain.Profil`.

- [x] **Step 4: Vérifier le succès**

Run `mvn -pl service-profil test -Dtest=ProfilTest` → vert.

- [x] **Step 5: hexagonal-boundary-guard**

Invoquer l'agent `hexagonal-boundary-guard` sur le diff. Attendu : "Aucun
import interdit détecté."

- [x] **Step 6: Commit**

```bash
git add service-profil/src/main/java/com/example/domain service-profil/src/test/java/com/example/MaDemo/domain
git commit -m "feat(profil): add domain Profil model and ProfilPort"
```

---

### Task 2: `ProfilJpaAdapter` remplace `ProfilRepository`

**Files:**
- Create: `service-profil/src/main/java/com/example/infrastructure/adapter/out/persistence/ProfilEntity.java`
- Create: `service-profil/src/main/java/com/example/infrastructure/adapter/out/persistence/ProfilJpaAdapter.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/infrastructure/adapter/out/persistence/ProfilJpaAdapterTest.java`
- Delete: `service-profil/src/main/java/com/example/entity/Profil.java`
- Delete: `service-profil/src/main/java/com/example/repository/ProfilRepository.java`
- Delete: `service-profil/src/test/java/com/example/MaDemo/repository/ProfilRepositoryTest.java` (remplacé par le test ci-dessus)

**Interfaces:**
- Produces: `ProfilJpaAdapter implements ProfilPort` (mapping
  `domain.Profil` ↔ `ProfilEntity` interne, `EntityManager` repris tel
  quel de l'ancien `ProfilRepository`, y compris le choix
  `getResultList()` plutôt que `getResultStream()` documenté en
  commentaire dans l'original — reprendre ce commentaire).

- [x] **Step 1: Adapter le test existant**

Partir de `ProfilRepositoryTest.java` (`@SpringBootTest @Transactional`) :
même scénarios (`save`/`findByPlayerId`/`existsByPlayerId`), mais
assertions sur `domain.Profil` au lieu de l'entité. Run → échoue (classe
`ProfilJpaAdapter` absente).

- [x] **Step 2: Implémenter `ProfilEntity` (renommage de l'entité JPA) et `ProfilJpaAdapter`**

`ProfilEntity` = copie de `entity/Profil.java` (annotations JPA
inchangées, renommée pour éviter la confusion avec `domain.Profil`).
`ProfilJpaAdapter` : `@Repository`, `@PersistenceContext EntityManager`,
implémente `ProfilPort`, mappe `ProfilEntity` → `domain.Profil` en sortie
et `domain.Profil` → `ProfilEntity` en entrée de `save`.

- [x] **Step 3: Vérifier le succès puis supprimer les anciens fichiers**

Run le test → vert. `git rm` `entity/Profil.java`,
`repository/ProfilRepository.java`, `ProfilRepositoryTest.java`.

- [x] **Step 4: hexagonal-boundary-guard + spring-java-reviewer**

`hexagonal-boundary-guard` (bloquant : `ProfilJpaAdapter` est bien en
`infrastructure.adapter.out` et implémente `domain.port.out.ProfilPort`,
aucun import interdit ailleurs). `spring-java-reviewer` (mapping
entité/domaine correct, pas de fuite de `ProfilEntity` hors de l'adapter).

- [x] **Step 5: Commit**

```bash
git add -A service-profil/src/main/java/com/example/infrastructure service-profil/src/test/java/com/example/MaDemo/infrastructure
git commit -m "refactor(profil): move ProfilRepository behind ProfilPort as JPA adapter"
```

---

### Task 3: `application.ProfilService` remplace `service/ProfilService`

**Files:**
- Create: `service-profil/src/main/java/com/example/application/ProfilService.java`
- Move: `service-profil/src/main/java/com/example/mapper/ProfilMapper.java` → `service-profil/src/main/java/com/example/application/ProfilMapper.java`
- Modify: `service-profil/src/main/java/com/example/controller/ProfilController.java` (dépend de `application.ProfilService`)
- Delete: `service-profil/src/main/java/com/example/service/ProfilService.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/application/ProfilServiceTest.java`

**Interfaces:**
- Produces: `application.ProfilService.getProfil(String playerId):ProfilDto`,
  `updateProfil(String playerId, UpdateProfilRequest):ProfilDto` — dépend
  uniquement de `ProfilPort` + `ProfilMapper` (qui mappe désormais
  `domain.Profil` ↔ `ProfilDto`, plus `ProfilEntity` ↔ `ProfilDto`).

- [x] **Step 1: Test unitaire pur (mock `ProfilPort`)**

Écrire `ProfilServiceTest` (Mockito, mock `ProfilPort`), reprenant les
scénarios de lecture/update de l'ancien service (y compris le cas
`ProfilNotFoundException`). Run → échoue.

- [x] **Step 2: Adapter `ProfilMapper` puis implémenter `application.ProfilService`**

`ProfilMapper` mappe désormais `domain.Profil` ↔ `ProfilDto` (au lieu de
l'entité JPA). `application.ProfilService` reprend la logique de
`service/ProfilService.java` telle quelle, en substituant
`ProfilRepository` par `ProfilPort`.

- [x] **Step 3: Vérifier le succès, rebrancher le controller**

Run le test unitaire → vert. Modifier `ProfilController` pour injecter
`application.ProfilService`. Run `ProfilControllerIT` (existant, inchangé
dans son intention) → doit rester vert.

- [x] **Step 4: Supprimer l'ancien service**

`git rm service-profil/src/main/java/com/example/service/ProfilService.java`.

- [x] **Step 5: hexagonal-boundary-guard + spring-java-reviewer**

- [x] **Step 6: Commit**

```bash
git commit -m "refactor(profil): move profile read/update use case to application layer"
```

---

### Task 4: `EventPublisherPort` + adapter outbox

**Files:**
- Create: `service-profil/src/main/java/com/example/domain/OutboxEventToPublish.java`
- Create: `service-profil/src/main/java/com/example/domain/port/out/EventPublisherPort.java`
- Create: `service-profil/src/main/java/com/example/infrastructure/adapter/out/outbox/OutboxEventPublisherAdapter.java`
- Move: `service-profil/src/main/java/com/example/outbox/OutboxEvent.java` → `service-profil/src/main/java/com/example/infrastructure/adapter/out/outbox/OutboxEvent.java`
- Move: `service-profil/src/main/java/com/example/outbox/OutboxEventRepository.java` → `service-profil/src/main/java/com/example/infrastructure/adapter/out/outbox/OutboxEventRepository.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/infrastructure/adapter/out/outbox/OutboxEventPublisherAdapterTest.java`

**Interfaces:**
- Produces (domain, aucune dépendance framework) :
  ```java
  package com.example.domain;

  public record OutboxEventToPublish(
      String aggregateType,
      String aggregateId,
      String eventType,
      Object payload
  ) {}
  ```
  ```java
  package com.example.domain.port.out;

  import com.example.domain.OutboxEventToPublish;

  public interface EventPublisherPort {
      void publish(OutboxEventToPublish event);
  }
  ```
- Consumes : `OutboxEventRepository` (JPA, inchangé) + `ObjectMapper`
  (Jackson) côté adapter uniquement.

- [x] **Step 1: Test de l'adapter (mock repo, `ObjectMapper` réel)**

Vérifie que `publish(...)` sérialise `payload` en JSON et persiste une
ligne `OutboxEvent` avec les bons `aggregatetype`/`aggregateid`/`type`.
Run → échoue.

- [x] **Step 2: Implémenter le record domaine, le port, l'adapter**

`OutboxEventPublisherAdapter implements EventPublisherPort`, `@Component`,
injecte `OutboxEventRepository` + `ObjectMapper`, reprend la logique de
sérialisation actuellement dans `ProfilCreationService`
(`JsonProcessingException` capturée/relancée de la même façon).

- [x] **Step 3: Vérifier le succès**

- [x] **Step 4: hexagonal-boundary-guard + spring-java-reviewer**

Vérifier que `domain.OutboxEventToPublish`/`EventPublisherPort` ne portent
aucun import Jackson/JPA — seule l'adapter en porte.

- [x] **Step 5: Commit**

```bash
git commit -m "feat(profil): add EventPublisherPort and outbox adapter"
```

---

### Task 5: `application.CreateProfilUseCase` remplace `service/ProfilCreationService`

**Files:**
- Create: `service-profil/src/main/java/com/example/application/CreateProfilUseCase.java`
- Test: `service-profil/src/test/java/com/example/MaDemo/application/CreateProfilUseCaseTest.java`
- Delete: `service-profil/src/main/java/com/example/service/ProfilCreationService.java`
- Delete: `service-profil/src/test/java/com/example/MaDemo/service/ProfilCreationServiceTest.java` (remplacé)

**Interfaces:**
- Produces: `CreateProfilUseCase.execute(PlayerRegisteredEvent event)` (ou
  signature équivalente à l'actuelle `onPlayerRegistered`) — dépend de
  `ProfilPort` + `EventPublisherPort` uniquement, **sans** `@Transactional`
  ni `@Service`.

- [x] **Step 1: Adapter le test existant**

`ProfilCreationServiceTest` est déjà Mockito pur (mock `ProfilRepository`
+ `OutboxEventRepository`) — le réécrire avec mock `ProfilPort` + mock
`EventPublisherPort`. Run → échoue.

- [x] **Step 2: Implémenter `CreateProfilUseCase`**

Reprendre l'orchestration de `ProfilCreationService.onPlayerRegistered`
(création du `domain.Profil`, `profilPort.save(...)`, construction de
l'`OutboxEventToPublish` correspondant à l'ancien payload JSON,
`eventPublisherPort.publish(...)`), sans annotation framework.

- [x] **Step 3: Vérifier le succès, supprimer l'ancien service**

- [x] **Step 4: hexagonal-boundary-guard (strict) + spring-java-reviewer**

Vérifier qu'aucun `@Transactional`/`ObjectMapper`/JPA ne subsiste dans
`CreateProfilUseCase`.

- [x] **Step 5: Commit**

```bash
git commit -m "refactor(profil): move profile creation use case to application layer"
```

---

### Task 6: Rebrancher les adapters d'entrée/sortie + frontière transactionnelle

**Files:**
- Move: `kafka/PlayerRegisteredConsumer.java`, `kafka/ProfilEventConsumer.java`, `kafka/TelemetryEventConsumer.java` → `infrastructure/adapter/in/kafka/`
- Move: `kafka/TelemetryEventProducer.java` → `infrastructure/adapter/out/kafka/`
- Move: `kafka/KafkaConsumerConfig.java`, `kafka/KafkaTopicConfig.java` → `infrastructure/config/`
- Move: `controller/ProfilController.java`, `controller/TelemetryController.java` → `infrastructure/adapter/in/web/`
- Move: `outbox/OutboxCleanupJob.java` → `infrastructure/adapter/out/outbox/`
- Move: `dto/ProfilDto.java`, `dto/UpdateProfilRequest.java`, `dto/TelemetryEventDto.java` → `application/dto/`
- Modify (imports + package) : `ProfilControllerIT`, `PlayerRegisteredFlowIT` en conséquence de leurs cibles déplacées
- Modify: `PlayerRegisteredConsumer` (ajout `@Transactional` sur la méthode qui déclenche `CreateProfilUseCase.execute`)

**Interfaces:**
- Aucune nouvelle signature — déplacement mécanique de packages + pose de
  `@Transactional` sur l'unique point d'entrée transactionnel restant.

- [x] **Step 1: Snapshot des tests avant déplacement**

Run `mvn -pl service-profil test` complet → noter l'état vert de
référence.

- [x] **Step 2: Déplacer fichier par fichier (`git mv`) et corriger les imports**

Un `git mv` par fichier listé ci-dessus, ajustement du `package` déclaré
et des imports dans les fichiers déplacés et leurs appelants.

- [x] **Step 3: Poser `@Transactional` sur `PlayerRegisteredConsumer`**

La méthode qui appelle `CreateProfilUseCase.execute(...)` porte désormais
`@Transactional` (elle ne l'avait pas avant — c'était `ProfilCreationService`
qui la portait ; l'atomicité écriture Profil + outbox doit être
préservée).

- [x] **Step 4: Vérifier le succès**

Run `mvn -pl service-profil test` complet, en particulier
`ProfilControllerIT` et `PlayerRegisteredFlowIT` (`@EmbeddedKafka`) →
vert, comportement inchangé.

- [x] **Step 5: hexagonal-boundary-guard + spring-java-reviewer**

`spring-java-reviewer` vérifie spécifiquement que l'écriture Profil +
ligne outbox reste dans une seule transaction malgré le déplacement.

- [x] **Step 6: Commit**

```bash
git commit -m "refactor(profil): move REST/Kafka adapters into infrastructure.adapter.in/out packages"
```

---

### Task 7: docs-sync service-profil

**Files:**
- Modify: `.claude/rules/hexagonal-architecture.md` (section "État actuel
  de la migration" : service-profil marqué migré, décrire le nouveau
  découpage réel)
- Modify: `service-profil/CLAUDE.md` (packages cibles → packages réels)
- Modify: `documents/ARCHITECTURE.md` si §5 décrit encore l'ancien
  découpage `Profil`/`ProfilDto`

- [x] **Step 1: Invoquer l'agent `docs-sync`**

Sur le diff cumulé des tâches 1-6.

- [x] **Step 2: Revue rapide du diff proposé**

Vérifier que les éditions restent minimales (pas de réécriture hors
périmètre).

- [x] **Step 3: Commit séparé**

```bash
git commit -m "docs: update migration status after service-profil hexagonalization"
```

---

## Phase B — service-identite (réutilise le pattern validé)

### Task 8: Domaine `Account` + `AccountPort`

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/domain/Account.java`
- Create: `service-identite/src/main/java/com/nebula/identite/domain/port/out/AccountPort.java`
- Test: `service-identite/src/test/java/com/nebula/identite/domain/AccountTest.java`

**Interfaces:**
- Produces: `domain.Account` (POJO : `id`, `username`, `email`,
  `passwordHash`, `createdAt` — champs de `entity/Account.java`, sans
  `@PrePersist`, sans annotation JPA).
- Produces: `AccountPort.save(Account):Account`,
  `existsByUsername(String):boolean`, `existsByEmail(String):boolean`,
  `findByUsername(String):Optional<Account>`.

- [x] **Step 1: Lire l'entité et le repository actuels**

Lire `entity/Account.java` (y compris le `@PrePersist` générant `id`
UUID et `createdAt`) et `repository/AccountRepository.java`.

- [x] **Step 2: Test qui échoue puis implémentation**

Même séquence que Task 1 (adaptée à `Account`). La génération de `id`
(UUID) et `createdAt` (`Instant.now()`), faite aujourd'hui par
`@PrePersist`, est déplacée dans le use case `application.RegisterUseCase`
(Task 12) — le modèle domaine reste un porteur de données pur.

- [x] **Step 3: Vérifier le succès**

- [x] **Step 4: hexagonal-boundary-guard**

- [x] **Step 5: Commit**

```bash
git commit -m "feat(identite): add domain Account model and AccountPort"
```

---

### Task 9: `AccountJpaAdapter` remplace `entity/Account` + `repository/AccountRepository`

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/infrastructure/adapter/out/persistence/AccountEntity.java`
- Move: `service-identite/src/main/java/com/nebula/identite/repository/AccountRepository.java` → `service-identite/src/main/java/com/nebula/identite/infrastructure/adapter/out/persistence/AccountRepository.java` (interface `JpaRepository<AccountEntity,String>` inchangée, retypée sur `AccountEntity`)
- Create: `service-identite/src/main/java/com/nebula/identite/infrastructure/adapter/out/persistence/AccountJpaAdapter.java`
- Test: `service-identite/src/test/java/com/nebula/identite/infrastructure/adapter/out/persistence/AccountJpaAdapterTest.java`
- Delete: `service-identite/src/main/java/com/nebula/identite/entity/Account.java`

**Interfaces:**
- Produces: `AccountJpaAdapter implements AccountPort`, délègue au
  `JpaRepository` interne (`AccountRepository`, déplacé) et mappe
  `AccountEntity` ↔ `domain.Account`.

- [x] **Step 1: Test d'intégration de l'adapter**

Reprendre le scénario implicite testé aujourd'hui via
`AuthControllerIT`/H2 pour `save`/`existsByUsername`/`existsByEmail`/
`findByUsername`, assertions sur `domain.Account`. Run → échoue.

- [x] **Step 2: Implémenter `AccountEntity` (copie de l'entité, `@PrePersist` conservé) et `AccountJpaAdapter`**

`AccountJpaAdapter` : `@Repository`, injecte le `JpaRepository`
`AccountRepository` interne, mappe en entrée/sortie.

- [x] **Step 3: Vérifier le succès, supprimer l'ancienne entité**

- [x] **Step 4: hexagonal-boundary-guard + spring-java-reviewer**

- [x] **Step 5: Commit**

```bash
git commit -m "refactor(identite): move AccountRepository behind AccountPort as JPA adapter"
```

---

### Task 10: `TokenPort` + `JwtTokenAdapter` remplace `service/JwtService`

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/domain/port/out/TokenPort.java`
- Create: `service-identite/src/main/java/com/nebula/identite/infrastructure/adapter/out/security/JwtTokenAdapter.java`
- Move: `service-identite/src/test/java/com/nebula/identite/service/JwtServiceTest.java` → `service-identite/src/test/java/com/nebula/identite/infrastructure/adapter/out/security/JwtTokenAdapterTest.java`
- Delete: `service-identite/src/main/java/com/nebula/identite/service/JwtService.java`

**Interfaces:**
- Produces:
  ```java
  package com.nebula.identite.domain.port.out;

  public interface TokenPort {
      String issue(String subject, String username, String role);
  }
  ```
- `JwtTokenAdapter implements TokenPort`, `@Service` (côté infra —
  autorisé), reprend le corps de `JwtService` tel quel (clé RSA via
  `JwtKeyConfig`, `io.jsonwebtoken.Jwts`).

- [x] **Step 1: Adapter le test existant**

`JwtServiceTest` (déjà Mockito pur, clé RSA en mémoire) : renommer la
classe testée `JwtService` → `JwtTokenAdapter`, adapter la signature
d'appel à `issue(...)` si le nom de méthode change. Run → échoue.

- [x] **Step 2: Implémenter `TokenPort` + `JwtTokenAdapter`**

- [x] **Step 3: Vérifier le succès, supprimer `JwtService`**

- [x] **Step 4: hexagonal-boundary-guard**

Vérifier que `TokenPort` (dans `domain.port.out`) ne porte aucun import
`io.jsonwebtoken.*`/Spring — seule l'adapter en porte.

- [x] **Step 5: Commit**

```bash
git commit -m "refactor(identite): extract TokenPort and move JWT issuance to infrastructure adapter"
```

---

### Task 11: `PasswordHasherPort` + `EventPublisherPort`/outbox

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/domain/port/out/PasswordHasherPort.java`
- Create: `service-identite/src/main/java/com/nebula/identite/infrastructure/adapter/out/security/BCryptPasswordHasherAdapter.java`
- Create: `service-identite/src/main/java/com/nebula/identite/domain/OutboxEventToPublish.java`
- Create: `service-identite/src/main/java/com/nebula/identite/domain/port/out/EventPublisherPort.java`
- Create: `service-identite/src/main/java/com/nebula/identite/infrastructure/adapter/out/outbox/OutboxEventPublisherAdapter.java`
- Move: `outbox/OutboxEvent.java`, `outbox/OutboxEventRepository.java`, `outbox/OutboxCleanupJob.java` → `infrastructure/adapter/out/outbox/`
- Test: `.../security/BCryptPasswordHasherAdapterTest.java`, `.../outbox/OutboxEventPublisherAdapterTest.java`

**Interfaces:**
- Produces: `PasswordHasherPort.hash(String raw):String`,
  `matches(String raw, String hash):boolean` — implémentation délègue à
  `BCryptPasswordEncoder` (bean existant de `PasswordEncoderConfig`,
  déplacé/réutilisé côté adapter).
- Produces: `EventPublisherPort.publish(OutboxEventToPublish)` (même
  contrat que Task 4, dupliqué pour ce service).

- [x] **Step 1: Tests des deux adapters**

`BCryptPasswordHasherAdapterTest` (hash puis matches, round-trip).
`OutboxEventPublisherAdapterTest` (mêmes assertions que Task 4, adapté à
`PlayerRegisteredEvent` de service-identite). Run → échouent.

- [x] **Step 2: Implémenter les deux ports + adapters**

- [x] **Step 3: Vérifier le succès**

- [x] **Step 4: hexagonal-boundary-guard + spring-java-reviewer**

- [x] **Step 5: Commit**

```bash
git commit -m "feat(identite): add PasswordHasherPort, EventPublisherPort and outbox adapter"
```

---

### Task 12: `RegisterUseCase` / `LoginUseCase` remplacent `service/AuthService`

**Files:**
- Create: `service-identite/src/main/java/com/nebula/identite/domain/DuplicateAccountException.java` (déplacé depuis `exception/`)
- Create: `service-identite/src/main/java/com/nebula/identite/domain/InvalidCredentialsException.java` (déplacé depuis `exception/`)
- Create: `service-identite/src/main/java/com/nebula/identite/application/RegisterUseCase.java`
- Create: `service-identite/src/main/java/com/nebula/identite/application/LoginUseCase.java`
- Move: `dto/RegisterRequest.java`, `dto/LoginRequest.java`, `dto/AuthResponse.java` → `application/dto/`
- Move: `event/PlayerRegisteredEvent.java` → `application/event/`
- Test: `application/RegisterUseCaseTest.java`, `application/LoginUseCaseTest.java`
- Move: `event/PlayerRegisteredEventTest.java` → `application/event/PlayerRegisteredEventTest.java`
- Delete: `service/AuthService.java`, `service/AuthServiceTest.java`

**Interfaces:**
- Produces: `RegisterUseCase.execute(RegisterRequest):AuthResponse`,
  `LoginUseCase.execute(LoginRequest):AuthResponse` — dépendent de
  `AccountPort`, `PasswordHasherPort`, `TokenPort`, `EventPublisherPort`,
  **sans** `@Transactional` ni `@Service`.

- [x] **Step 1: Scinder `AuthServiceTest` en deux**

`AuthServiceTest` mocke déjà `AccountRepository`/`JwtService`/
`OutboxEventRepository` (Mockito pur) — le scinder en
`RegisterUseCaseTest` (mock `AccountPort`, `PasswordHasherPort`,
`TokenPort`, `EventPublisherPort`) et `LoginUseCaseTest` (mock
`AccountPort`, `PasswordHasherPort`, `TokenPort`), en reprenant tous les
scénarios existants (doublon username/email, credentials invalides,
absence de PII dans l'événement publié). Run → échouent.

- [x] **Step 2: Implémenter les deux use cases**

Reprendre l'orchestration de `AuthService.register`/`login` telle quelle,
en substituant chaque dépendance concrète par son port. `register`
construit désormais lui-même `id` (UUID) et `createdAt` (`Instant.now()`)
pour `domain.Account` avant `accountPort.save(...)` (logique déplacée
depuis `@PrePersist`, Task 8).

- [x] **Step 3: Vérifier le succès, supprimer `AuthService`**

- [x] **Step 4: hexagonal-boundary-guard (strict) + spring-java-reviewer**

Vérifier qu'aucun `PasswordEncoder`/`ObjectMapper`/`@Transactional` ne
subsiste dans les deux use cases.

- [x] **Step 5: Commit**

```bash
git commit -m "refactor(identite): move register/login use cases to application layer"
```

---

### Task 13: Rebrancher `AuthController` + config + frontière transactionnelle

**Files:**
- Move: `controller/AuthController.java` → `infrastructure/adapter/in/web/`
- Move: `config/SecurityConfig.java`, `config/PasswordEncoderConfig.java`, `config/JwtKeyConfig.java`, `kafka/KafkaTopicConfig.java` → `infrastructure/config/`
- Modify: `AuthControllerIT` (imports/package en conséquence)
- Modify: `AuthController.register` (ajout `@Transactional`, dépend de `RegisterUseCase` + `LoginUseCase`)

**Interfaces:**
- Aucune nouvelle signature — déplacement + pose de `@Transactional`.

- [x] **Step 1: `git mv` fichier par fichier, ajustement imports**

- [x] **Step 2: Poser `@Transactional` sur `AuthController.register`**

Même raisonnement que Task 6 pour `PlayerRegisteredConsumer` : c'était
`AuthService.register` qui portait `@Transactional`, l'atomicité
écriture Account + outbox doit être préservée au niveau de l'adapter
d'entrée.

- [x] **Step 3: Vérifier le succès**

Run `mvn -pl service-identite test` complet, en particulier
`AuthControllerIT` → vert.

- [x] **Step 4: hexagonal-boundary-guard + spring-java-reviewer**

`spring-java-reviewer` vérifie la cohérence de la frontière
transactionnelle avec le pattern déjà validé sur service-profil (Task 6).

- [x] **Step 5: Commit**

```bash
git commit -m "refactor(identite): move REST adapter and config into infrastructure packages"
```

---

### Task 14: docs-sync service-identite

**Files:**
- Modify: `.claude/rules/hexagonal-architecture.md` (les deux services
  marqués migrés)
- Modify: `service-identite/CLAUDE.md`
- Modify: `documents/ARCHITECTURE.md` si nécessaire

- [x] **Step 1: Invoquer l'agent `docs-sync`** sur le diff cumulé des
  tâches 8-13.

- [x] **Step 2: Revue rapide du diff proposé**

- [x] **Step 3: Commit séparé**

```bash
git commit -m "docs: update migration status after service-identite hexagonalization"
```

---

## Phase C — Vérification globale

### Task 15: Vérification e2e post-migration

**Files:** aucun changement de code attendu.

**Interfaces:** aucune — tâche de vérification pure.

- [x] **Step 1: Lancer le scénario e2e**

Invoquer l'agent `e2e-verifier` (ou skill `e2e-verify`) : `make setup` →
`POST /auth/register` → vérifier `players.registered` publié → vérifier
la création automatique du profil (outbox + Debezium) → `make logs` →
`make down`, propre.

- [x] **Step 2: Comparer au comportement pré-migration**

Aucune régression fonctionnelle attendue (migration interne pure) :
mêmes codes HTTP, même payload d'événement, même latence de publication
outbox de l'ordre de quelques centaines de ms.

- [x] **Step 3: Correctif si nécessaire**

Si régression détectée : corriger, relancer le scénario, commit `fix:`
dédié. Si tout est conforme, ne rien committer — c'est la condition de
clôture du plan.
