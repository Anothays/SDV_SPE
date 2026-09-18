# Plan 3 — service-identite → service-sso, service-profil → service-role-manager (RBAC)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lever l'ambiguïté identite/profil : `service-identite` devient
`service-sso` (authentification seule, inchangée fonctionnellement) et
`service-profil` devient `service-role-manager` (nouveau domaine RBAC : un
rôle `PLAYER|MODERATOR|ADMIN` par joueur, attribué par défaut à
l'inscription, modifiable par un admin). `role-manager` est la source de
vérité des rôles et publie `access.role.assigned` via outbox ; `sso`
projette ce rôle dans sa table `account` et l'émet dans le claim `role` du
JWT. Renommage complet : dossiers, packages Java, artefacts Maven, compose,
bases MySQL, connecteurs Debezium, monitoring, JMeter, CI, docs.

**Architecture:** Spec `docs/superpowers/specs/2026-09-18-sso-role-manager.md`.
Les deux services restent hexagonaux (`domain` / `application` /
`infrastructure.adapter.{in,out}`). Chorégraphie : `POST /auth/register`
(sso) → tx compte + outbox `players.registered` → Debezium → `role-manager`
consomme, tx `role_assignment(PLAYER)` + outbox `access.role.assigned` →
Debezium → `sso` consomme et met à jour `account.role` (idempotent).
`PUT /api/roles/{playerId}` (admin, Basic in-memory conservé) suit le même
chemin outbox → sso. Le domaine profil (`level`, `region`) disparaît ; la
télémétrie reste dans `role-manager` (impureté documentée, spec §3.3).

**Tech Stack:** Java 17, Spring Boot 3.4.1, spring-data-jpa, spring-kafka,
JJWT 0.12.6, Jackson, MySQL 8 (binlog ROW), Debezium 3.0 (EventRouter), H2
(tests), spring-kafka-test (EmbeddedKafka), JUnit 5 + Mockito + Awaitility,
Docker Compose, Prometheus/Grafana, JMeter.

## Global Constraints

- Plan exécuté depuis la racine du repo :
  `C:\Users\jeremysananikone\projets\SDV_SPE` (Git Bash).
- **Renommages via `git mv`** (dossiers de module, dossiers de packages,
  fichiers JSON/SQL/JMX) pour préserver l'historique. Les remplacements
  de texte se font avec `sed`/`grep` **limités au module concerné**
  (jamais `sed` récursif sur toute la racine : les specs/plans datés,
  `documents/*.docx/pptx` et l'historique ne sont pas retouchés).
- Après chaque tâche de renommage, vérification zéro-résidu (hors
  `target/`, `.git/`, `docs/superpowers/`, `documents/*.docx|pptx`) :
  `grep -rn --exclude-dir=target --exclude-dir=.git --exclude-dir=docs --exclude='*.docx' --exclude='*.pptx' -e 'com.example' -e 'com.nebula.identite' -e 'MaDemo' -e 'maBase' -e 'service-identite' -e 'service-profil' .`
  Attendu en fin de plan : aucune ligne (la tâche 12 traite les derniers
  fichiers : `CLAUDE.md`, règles, agents, skills).
- `domain`/`application` : jamais d'import `org.springframework.*`,
  `jakarta.persistence.*`, `org.apache.kafka.*`, `com.fasterxml.jackson.*`.
  **`hexagonal-boundary-guard` est bloquant en fin de toute tâche
  touchant du Java** ; `spring-java-reviewer` en complément (ses findings
  bloquants corrigés avant commit).
- Pas de `domain.port.in` ; use cases = classes simples dans
  `application`, câblées dans `infrastructure.config.UseCaseConfig`.
  `@Transactional` uniquement sur les adapters d'entrée.
- Contrat outbox inchangé : entité métier + ligne `outbox_event` dans la
  **même transaction** ; colonnes `id`, `aggregatetype`, `aggregateid`,
  `type`, `payload`, `occurred_at` — le connecteur Debezium route sur
  `aggregatetype`, clé = `aggregateid`.
- `players.registered` v1 : contrat **inchangé** (aucune PII ; `email`
  reste confiné à sso). `access.role.assigned` v1 : `eventId`,
  `eventVersion`, `occurredAt`, `playerId`, `role` — aucune PII.
- Tests : depuis le répertoire du service, `mvn test` (ou `./mvnw test`).
  Les tests du module role-manager passent du préfixe hérité
  `com.example.MaDemo.*` à `com.nebula.rolemanager.*` (même arborescence
  que le code prod) — c'est la seule occasion de solder cette dette.
- Commits conventionnels en anglais, un commit par tâche. Scopes :
  `sso`, `role-manager`, `messaging`, `monitoring`, `load-testing`, `ci`,
  `docs`.
- Fichiers non suivis au démarrage (`CLAUDE.md` racine, `.claude/agents/`,
  `.claude/skills/`, `AGENTS.md`) : modifiés en tâche 12 et ajoutés au
  commit `docs:` correspondant (le `CLAUDE.md` racine se déclare
  « checked into the codebase »). Les `.docx`/`.pptx` non suivis de
  `documents/` ne sont **jamais** ajoutés par ce plan.
- Stack éphémère : aucune migration de données (MySQL sans volume nommé,
  `ddl-auto=update`). Les changements de `group-id` Kafka repartent de
  `earliest` — sans effet en dev.

---

## Phase 0 — Préparation

### Task 0: Committer les modifications en attente

**Files:**
- Modify (déjà modifiés dans l'arbre) : `service-messaging/debezium/identite-outbox-connector.json`, `service-messaging/debezium/profil-outbox-connector.json` (`"include.schema.changes": "false"`)
- Modify (déjà modifié) : `docs/superpowers/plans/2026-07-18-plan-1-socle-flux-inscription.md` (cases cochées)

**Interfaces:** aucune.

- [x] **Step 1: Vérifier l'état réel**

Run: `git status --short` → les trois fichiers ci-dessus en `M`. Si
l'utilisateur les a déjà committés, passer à la Task 1.

- [x] **Step 2: Deux commits séparés**

```bash
git add service-messaging/debezium/identite-outbox-connector.json service-messaging/debezium/profil-outbox-connector.json
git commit -m "fix(messaging): disable schema change events on outbox connectors"
git add docs/superpowers/plans/2026-07-18-plan-1-socle-flux-inscription.md
git commit -m "docs: mark plan-1 tasks as completed"
```

---

## Phase A — service-identite → service-sso

### Task 1: Renommer le module et le package `com.nebula.identite` → `com.nebula.sso`

**Files:**
- Move: `service-identite/` → `service-sso/`
- Move: `service-sso/src/main/java/com/nebula/identite/` → `service-sso/src/main/java/com/nebula/sso/`
- Move: `service-sso/src/test/java/com/nebula/identite/` → `service-sso/src/test/java/com/nebula/sso/`
- Move: `.../sso/IdentiteApplication.java` → `.../sso/SsoApplication.java` ; `.../sso/IdentiteApplicationTests.java` → `.../sso/SsoApplicationTests.java`
- Modify: `service-sso/pom.xml` (artifactId, name : `service-sso`)
- Modify: `service-sso/src/main/resources/application.properties` (`spring.application.name=service-sso`, datasource `jdbc:mysql://localhost:3306/sso`, commentaires)
- Modify: `service-sso/src/test/resources/application.properties` (`jdbc:h2:mem:sso`)
- Modify: tous les `.java` du module (déclarations `package` et `import`)

**Interfaces:**
- Produces: module Maven `service-sso`, package racine `com.nebula.sso`,
  comportement identique (mêmes endpoints `/auth/register`, `/auth/login`,
  même JWT, même outbox).

- [x] **Step 1: Déplacer avec git mv**

```bash
git mv service-identite service-sso
git mv service-sso/src/main/java/com/nebula/identite service-sso/src/main/java/com/nebula/sso
git mv service-sso/src/test/java/com/nebula/identite service-sso/src/test/java/com/nebula/sso
git mv service-sso/src/main/java/com/nebula/sso/IdentiteApplication.java service-sso/src/main/java/com/nebula/sso/SsoApplication.java
git mv service-sso/src/test/java/com/nebula/sso/IdentiteApplicationTests.java service-sso/src/test/java/com/nebula/sso/SsoApplicationTests.java
```

Supprimer `service-sso/target/` s'il existe (artefacts obsolètes,
ignorés par git).

- [x] **Step 2: Remplacer les identifiants dans le module uniquement**

```bash
grep -rl --exclude-dir=target 'com\.nebula\.identite\|IdentiteApplication' service-sso | xargs sed -i 's/com\.nebula\.identite/com.nebula.sso/g; s/IdentiteApplication/SsoApplication/g'
sed -i 's|<artifactId>service-identite</artifactId>|<artifactId>service-sso</artifactId>|; s|<name>service-identite</name>|<name>service-sso</name>|' service-sso/pom.xml
sed -i 's/spring.application.name=service-identite/spring.application.name=service-sso/; s|jdbc:mysql://localhost:3306/identite|jdbc:mysql://localhost:3306/sso|' service-sso/src/main/resources/application.properties
sed -i 's/jdbc:h2:mem:identite/jdbc:h2:mem:sso/' service-sso/src/test/resources/application.properties
```

Relire ensuite à la main les commentaires de
`application.properties` (« base "identite" », « service-profil » → « base
"sso" », « service-role-manager ») et les Javadoc mentionnant « service
Identité » (`JwtTokenAdapter`, `PlayerRegisteredEvent`) : remplacer par
« service SSO ». Ne pas toucher `service-sso/CLAUDE.md` ici (Task 12).

- [x] **Step 3: Vérifier**

Run: `cd service-sso && mvn test` → vert (mêmes tests qu'avant, renommés).
Run: `grep -rn --exclude-dir=target 'identite' service-sso` → seules
occurrences restantes : `service-sso/CLAUDE.md` (traité en Task 12).

- [x] **Step 4: hexagonal-boundary-guard**

Invoquer `hexagonal-boundary-guard` sur le diff. Attendu : « Aucun import
interdit détecté » (renommage pur).

- [x] **Step 5: Commit**

```bash
git add -A service-identite service-sso
git commit -m "refactor(sso): rename service-identite module and package to service-sso"
```

---

### Task 2: Renommer l'infra de sso (compose, base MySQL, connecteur Debezium)

**Files:**
- Modify: `docker-compose.yml` (service `identite` → `sso`)
- Move: `service-messaging/mysql-init/create-identite-db.sql` → `service-messaging/mysql-init/create-sso-db.sql`
- Move: `service-messaging/debezium/identite-outbox-connector.json` → `service-messaging/debezium/sso-outbox-connector.json`

**Interfaces:**
- Produces: service compose `sso` (container `sso`, port 8082, base
  `sso`), connecteur Kafka Connect nommé `sso-outbox-connector`
  (`topic.prefix=sso`, `schema-history.sso`), toujours routé vers
  `players.registered`.

- [x] **Step 1: docker-compose.yml**

Dans le bloc `identite:` : clé de service `sso:`, `container_name: "sso"`,
`build: service-sso`, `SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/sso`.
Volume `db` :
`./service-messaging/mysql-init/create-sso-db.sql:/docker-entrypoint-initdb.d/1-create-sso-db.sql:ro`.
Commentaire de `kafka-connect-init` : « (sso, profil) » (profil devient
role-manager en Task 4).

- [x] **Step 2: SQL d'init et connecteur**

```bash
git mv service-messaging/mysql-init/create-identite-db.sql service-messaging/mysql-init/create-sso-db.sql
sed -i 's/identite/sso/g' service-messaging/mysql-init/create-sso-db.sql
git mv service-messaging/debezium/identite-outbox-connector.json service-messaging/debezium/sso-outbox-connector.json
sed -i 's/"database.include.list": "identite"/"database.include.list": "sso"/; s/"topic.prefix": "identite"/"topic.prefix": "sso"/; s/"table.include.list": "identite.outbox_event"/"table.include.list": "sso.outbox_event"/; s/schema-history.identite/schema-history.sso/' service-messaging/debezium/sso-outbox-connector.json
```

`database.server.id` (184056) inchangé. Relire le commentaire du SQL
(« la base "sso" est créée ici »).

- [x] **Step 3: Vérifier**

Run: `docker compose config -q && echo OK` → `OK`.
Run: `grep -rn 'identite' docker-compose.yml service-messaging` → aucune
ligne.

- [x] **Step 4: Commit**

```bash
git add docker-compose.yml service-messaging
git commit -m "chore(sso): rename compose service, database and Debezium connector"
```

---

## Phase B — service-profil → service-role-manager (renommage pur)

### Task 3: Renommer le module et les packages (`com.example` → `com.nebula.rolemanager`)

**Files:**
- Move: `service-profil/` → `service-role-manager/`
- Move: `service-role-manager/src/main/java/com/example/` → `service-role-manager/src/main/java/com/nebula/rolemanager/`
- Move: `service-role-manager/src/test/java/com/example/MaDemo/` → `service-role-manager/src/test/java/com/nebula/rolemanager/` (puis supprimer le dossier vide `src/test/java/com/example`)
- Move: `MaDemoApplication.java` → `RoleManagerApplication.java` ; `MaDemoApplicationTests.java` → `RoleManagerApplicationTests.java`
- Modify: `service-role-manager/pom.xml` (groupId `com.nebula`, artifactId/name `service-role-manager`, description « Service RBAC : attribution des rôles joueurs (Nebula) »)
- Modify: `service-role-manager/src/main/resources/application.properties` et `src/test/resources/application.properties`
- Modify: tous les `.java` du module (`package`/`import`, `groupId` Kafka)

**Interfaces:**
- Produces: module Maven `service-role-manager`, package racine
  `com.nebula.rolemanager` (prod **et** tests), comportement encore
  identique (domaine profil intact jusqu'à la Phase C).

- [x] **Step 1: Déplacer avec git mv**

```bash
git mv service-profil service-role-manager
mkdir -p service-role-manager/src/main/java/com/nebula service-role-manager/src/test/java/com/nebula
git mv service-role-manager/src/main/java/com/example service-role-manager/src/main/java/com/nebula/rolemanager
git mv service-role-manager/src/test/java/com/example/MaDemo service-role-manager/src/test/java/com/nebula/rolemanager
rmdir service-role-manager/src/test/java/com/example 2>/dev/null || true
git mv service-role-manager/src/main/java/com/nebula/rolemanager/MaDemoApplication.java service-role-manager/src/main/java/com/nebula/rolemanager/RoleManagerApplication.java
git mv service-role-manager/src/test/java/com/nebula/rolemanager/MaDemoApplicationTests.java service-role-manager/src/test/java/com/nebula/rolemanager/RoleManagerApplicationTests.java
```

Supprimer `service-role-manager/target/` s'il existe.

- [x] **Step 2: Remplacer les identifiants dans le module uniquement**

Ordre important : `com.example.MaDemo` avant `com.example`.

```bash
grep -rl --exclude-dir=target 'com\.example\|MaDemoApplication' service-role-manager | xargs sed -i 's/com\.example\.MaDemo/com.nebula.rolemanager/g; s/com\.example/com.nebula.rolemanager/g; s/MaDemoApplication/RoleManagerApplication/g'
sed -i 's|<groupId>com.example</groupId>|<groupId>com.nebula</groupId>|; s|<artifactId>MaDemo</artifactId>|<artifactId>service-role-manager</artifactId>|; s|<name>MaDemo</name>|<name>service-role-manager</name>|; s|<description>Demo project for Spring Boot with Hibernate / JPA</description>|<description>Service RBAC : attribution des rôles joueurs (Nebula)</description>|' service-role-manager/pom.xml
```

`application.properties` (main) : `spring.application.name=service-role-manager`,
`spring.datasource.url=jdbc:mysql://localhost:3306/role_manager`,
`spring.kafka.consumer.group-id=role-manager`,
`spring.kafka.consumer.properties.spring.json.trusted.packages=com.nebula.rolemanager.application.dto`
(l'ancienne valeur `com.example.dto` était déjà obsolète).
`application.properties` (test) : `jdbc:h2:mem:role_manager`, mêmes
`group-id`/`trusted.packages`.
Groupes Kafka dans le code : `ProfilEventConsumer` `"mademo"` →
`"role-manager"`, `TelemetryEventConsumer` `"mademo-telemetry"` →
`"role-manager-telemetry"`, `PlayerRegisteredConsumer` `"profil-service"`
→ `"role-manager"`.

- [x] **Step 3: Vérifier**

Run: `cd service-role-manager && mvn test` → vert.
Run: `grep -rn --exclude-dir=target -e 'com\.example' -e 'MaDemo' -e 'mademo' service-role-manager`
→ seules occurrences : `service-role-manager/CLAUDE.md` (Task 12).

- [x] **Step 4: hexagonal-boundary-guard**

Attendu : « Aucun import interdit détecté ».

- [x] **Step 5: Commit**

```bash
git add -A service-profil service-role-manager
git commit -m "refactor(role-manager): rename service-profil module and packages to service-role-manager"
```

---

### Task 4: Renommer l'infra de role-manager (compose, base, Debezium, Prometheus, Makefile)

**Files:**
- Modify: `docker-compose.yml` (service `app` → `role-manager`, `db.MYSQL_DATABASE`, `depends_on` de `prometheus` et `jmeter`)
- Move: `service-messaging/debezium/profil-outbox-connector.json` → `service-messaging/debezium/role-manager-outbox-connector.json`
- Modify: `service-monitoring/prometheus/prometheus.yml`, `service-monitoring/prometheus/alert-rules.yml`
- Modify: `Makefile` (`logs`)

**Interfaces:**
- Produces: service compose `role-manager` (container `role-manager`,
  port 8080, base `role_manager`), connecteur `role-manager-outbox-connector`
  (`topic.prefix=role-manager`), Prometheus scrape `role-manager:8080` **et**
  `sso:8082`, alerte `AppDown` couvrant les deux.

- [x] **Step 1: docker-compose.yml**

Bloc `app:` → `role-manager:`, `container_name: "role-manager"`,
`build: service-role-manager`, `SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/role_manager`.
`db.environment.MYSQL_DATABASE: role_manager`. `prometheus.depends_on` et
`jmeter.depends_on` : `app` → `role-manager`. Commentaire
`kafka-connect-init` : « (sso, role-manager) ».

- [x] **Step 2: Connecteur Debezium**

```bash
git mv service-messaging/debezium/profil-outbox-connector.json service-messaging/debezium/role-manager-outbox-connector.json
sed -i 's/"database.include.list": "maBase"/"database.include.list": "role_manager"/; s/"topic.prefix": "profil"/"topic.prefix": "role-manager"/; s/"table.include.list": "maBase.outbox_event"/"table.include.list": "role_manager.outbox_event"/; s/schema-history.profil/schema-history.role-manager/' service-messaging/debezium/role-manager-outbox-connector.json
```

`database.server.id` (184055) inchangé.

- [x] **Step 3: Prometheus + alertes**

`prometheus.yml` : remplacer le job `spring-boot-app` par deux jobs :

```yaml
  - job_name: 'role-manager'
    static_configs:
      - targets: ['role-manager:8080']
        labels:
          application: 'role-manager'
    metrics_path: '/actuator/prometheus'
  - job_name: 'sso'
    static_configs:
      - targets: ['sso:8082']
        labels:
          application: 'sso'
    metrics_path: '/actuator/prometheus'
```

`alert-rules.yml` : `groups[0].name: nebula-alerts`, `AppDown.expr:
up{job=~"role-manager|sso"} == 0`. Les autres règles agrègent sans filtre
de job : inchangées.

- [x] **Step 4: Makefile**

`logs: docker compose logs -f role-manager sso` (aide : « Suit les logs de
role-manager et sso »).

- [x] **Step 5: Vérifier**

Run: `docker compose config -q && echo OK` → `OK`.
Run: `grep -rn -e 'maBase' -e '\bapp\b' -e 'profil' docker-compose.yml service-messaging service-monitoring/prometheus Makefile`
→ seules occurrences : cibles `load-test`/`stress-test` du Makefile
(`profil-api-*.jmx`, traitées en Task 11).

- [x] **Step 6: Commit**

```bash
git add docker-compose.yml service-messaging service-monitoring/prometheus Makefile
git commit -m "chore(role-manager): rename compose service, database, Debezium connector and Prometheus targets"
```

---

## Phase C — Domaine RBAC dans service-role-manager

Préfixes utilisés ci-dessous : `RM=service-role-manager/src/main/java/com/nebula/rolemanager`,
`RMT=service-role-manager/src/test/java/com/nebula/rolemanager`.

### Task 5: Domaine `Role`, `RoleAssignment`, `RoleAssignmentPort`

**Files:**
- Create: `$RM/domain/Role.java`
- Create: `$RM/domain/RoleAssignment.java`
- Create: `$RM/domain/port/out/RoleAssignmentPort.java`
- Test: `$RMT/domain/RoleAssignmentTest.java`

**Interfaces:**
- Produces: `enum Role { PLAYER, MODERATOR, ADMIN }` (les trois rôles du
  §10 ARCHITECTURE.md).
- Produces: `RoleAssignment` POJO : `Long id`, `String playerId`,
  `Role role`, `Instant assignedAt`, `Instant updatedAt` (getters/setters,
  aucune annotation). `RoleAssignment.defaultFor(String playerId)` →
  `role=PLAYER`.
- Produces: `RoleAssignmentPort.save(RoleAssignment):RoleAssignment`,
  `findByPlayerId(String):Optional<RoleAssignment>`,
  `existsByPlayerId(String):boolean`.

- [x] **Step 1: Test qui échoue**

`RoleAssignmentTest` : `defaultFor("uuid-1")` → `PLAYER`, `playerId`
conservé ; setters/getters. Run
`mvn test -Dtest=RoleAssignmentTest` → échoue (classes absentes).

- [x] **Step 2: Implémenter** les trois types (imports Java standard
  uniquement).

- [x] **Step 3: Vérifier** → vert.

- [x] **Step 4: hexagonal-boundary-guard** → « Aucun import interdit ».

- [x] **Step 5: Commit**

```bash
git add service-role-manager/src/main/java/com/nebula/rolemanager/domain service-role-manager/src/test/java/com/nebula/rolemanager/domain/RoleAssignmentTest.java
git commit -m "feat(role-manager): add Role, RoleAssignment domain model and RoleAssignmentPort"
```

---

### Task 6: `RoleAssignmentJpaAdapter` (persistance)

**Files:**
- Create: `$RM/infrastructure/adapter/out/persistence/RoleAssignmentEntity.java`
- Create: `$RM/infrastructure/adapter/out/persistence/RoleAssignmentRepository.java`
- Create: `$RM/infrastructure/adapter/out/persistence/RoleAssignmentJpaAdapter.java`
- Test: `$RMT/infrastructure/adapter/out/persistence/RoleAssignmentJpaAdapterTest.java`

**Interfaces:**
- Produces: `RoleAssignmentEntity` `@Entity @Table(name="role_assignment")` :
  `@Id @GeneratedValue(IDENTITY) Long id` ; `player_id` `nullable=false,
  unique=true, length=36` ; `@Enumerated(EnumType.STRING) Role role`
  `nullable=false, length=16` ; `assigned_at` `nullable=false` (posé en
  `@PrePersist`) ; `updated_at` (posé en `@PreUpdate`).
- Produces: `RoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, Long>`
  avec `findByPlayerId`, `existsByPlayerId` (même style que
  `AccountRepository` de sso).
- Produces: `RoleAssignmentJpaAdapter implements RoleAssignmentPort`
  (`@Repository`, mapping entité ↔ domaine privé à l'adapter ; sur `save`
  d'un objet déjà persisté, préserver `assignedAt`).

- [x] **Step 1: Test qui échoue** (`@SpringBootTest @Transactional`, modèle :
  `ProfilJpaAdapterTest`) : save puis find (`assignedAt` non nul, rôle
  `PLAYER`), `existsByPlayerId` faux pour inconnu, update de rôle conserve
  `assignedAt` et pose `updatedAt`.

- [x] **Step 2: Implémenter** entité, repository, adapter.

- [x] **Step 3: Vérifier** → vert. (H2 `ddl-auto=create-drop` crée
  `role_assignment` ; MySQL via `ddl-auto=update` en Docker.)

- [x] **Step 4: hexagonal-boundary-guard + spring-java-reviewer**
  (aucune fuite de `RoleAssignmentEntity` hors de l'adapter).

- [x] **Step 5: Commit**

```bash
git add service-role-manager/src/main/java/com/nebula/rolemanager/infrastructure/adapter/out/persistence service-role-manager/src/test/java/com/nebula/rolemanager/infrastructure/adapter/out/persistence/RoleAssignmentJpaAdapterTest.java
git commit -m "feat(role-manager): persist role assignments behind RoleAssignmentPort"
```

---

### Task 7: Use cases RBAC + contrat `access.role.assigned`

**Files:**
- Create: `$RM/application/event/RoleAssignedEvent.java`
- Create: `$RM/application/AssignDefaultRoleUseCase.java`
- Create: `$RM/application/ChangeRoleUseCase.java`
- Create: `$RM/application/RoleAssignmentQueryService.java`
- Create: `$RM/application/RoleAssignmentMapper.java`
- Create: `$RM/application/dto/RoleAssignmentDto.java`, `$RM/application/dto/ChangeRoleRequest.java`
- Create: `$RM/exception/RoleAssignmentNotFoundException.java` (`@ResponseStatus(NOT_FOUND)`, même style que `ProfilNotFoundException` — package `exception` hors `application`, comme aujourd'hui)
- Test: `$RMT/application/AssignDefaultRoleUseCaseTest.java`, `$RMT/application/ChangeRoleUseCaseTest.java`, `$RMT/application/RoleAssignmentQueryServiceTest.java`, `$RMT/application/event/RoleAssignedEventTest.java`

**Interfaces:**
- Produces: `record RoleAssignedEvent(String eventId, int eventVersion,
  String occurredAt, String playerId, Role role)` +
  `static from(RoleAssignment)` (UUID, version 1, `Instant.now()`).
  Sérialisé par l'adapter outbox → `"role":"PLAYER"` (enum en String).
  Javadoc : « Contrat access.role.assigned v1 (spec §3.4). Aucune PII. »
- Produces: `AssignDefaultRoleUseCase(RoleAssignmentPort, EventPublisherPort)
  .execute(PlayerRegisteredEvent)` : `playerId` null/blanc →
  `IllegalArgumentException` ; déjà existant → log + return (idempotence)
  ; sinon `save(RoleAssignment.defaultFor(playerId))` puis
  `publish(new OutboxEventToPublish("access.role.assigned", playerId,
  "RoleAssigned", RoleAssignedEvent.from(saved)))`. Constante
  `ACCESS_ROLE_ASSIGNED_TOPIC = "access.role.assigned"` dans le use case
  (commentaire : doit rester identique à `KafkaTopicConfig` et au routage
  Debezium).
- Produces: `ChangeRoleUseCase(RoleAssignmentPort, EventPublisherPort)
  .execute(String playerId, Role role):RoleAssignmentDto` : introuvable →
  `RoleAssignmentNotFoundException` ; sinon `setRole`, `save`, `publish`
  (même événement), retourne le DTO. Rôle identique → sauvegarde et
  publie quand même (simplicité ; idempotence gérée côté sso).
- Produces: `RoleAssignmentQueryService(RoleAssignmentPort)
  .findByPlayerId(String):RoleAssignmentDto` (404 si absent).
- Produces: `RoleAssignmentDto(String playerId, Role role, Instant
  assignedAt, Instant updatedAt)` (record) ;
  `ChangeRoleRequest(@NotNull Role role)` (record, `jakarta.validation`
  autorisé en `application.dto` — déjà le cas de `TelemetryEventDto`).

- [x] **Step 1: Tests unitaires purs** (Mockito, modèle
  `CreateProfilUseCaseTest`) : création `PLAYER` + capture de
  `OutboxEventToPublish` (`aggregateType=access.role.assigned`,
  `aggregateId=playerId`, payload `RoleAssignedEvent` avec
  `role=PLAYER`) ; idempotence ; `playerId` manquant ; `ChangeRoleUseCase`
  404 / succès + publication ; query 404 / succès ; `RoleAssignedEvent.from`
  remplit `eventId`/`occurredAt`. Run → échouent.

- [x] **Step 2: Implémenter** les classes ci-dessus.

- [x] **Step 3: Vérifier** → vert (les anciens tests profil restent verts
  aussi : rien n'est encore supprimé).

- [x] **Step 4: hexagonal-boundary-guard** (bloquant) +
  `spring-java-reviewer`.

- [x] **Step 5: Commit**

```bash
git add service-role-manager/src/main/java/com/nebula/rolemanager/application service-role-manager/src/main/java/com/nebula/rolemanager/exception/RoleAssignmentNotFoundException.java service-role-manager/src/test/java/com/nebula/rolemanager/application
git commit -m "feat(role-manager): add role assignment use cases and access.role.assigned contract"
```

---

### Task 8: Adapters d'entrée, config, suppression du domaine profil

**Files:**
- Create: `$RM/infrastructure/adapter/in/web/RoleController.java`
- Modify: `$RM/infrastructure/adapter/in/kafka/PlayerRegisteredConsumer.java` (→ `AssignDefaultRoleUseCase`)
- Modify: `$RM/infrastructure/config/UseCaseConfig.java`, `KafkaTopicConfig.java`, `KafkaConsumerConfig.java`, `SecurityConfig.java`
- Delete: `$RM/domain/Profil.java`, `$RM/domain/port/out/ProfilPort.java`, `$RM/application/ProfilService.java`, `$RM/application/ProfilMapper.java`, `$RM/application/CreateProfilUseCase.java`, `$RM/application/dto/ProfilDto.java`, `$RM/application/dto/UpdateProfilRequest.java`, `$RM/exception/ProfilNotFoundException.java`, `$RM/infrastructure/adapter/in/web/ProfilController.java`, `$RM/infrastructure/adapter/in/kafka/ProfilEventConsumer.java`, `$RM/infrastructure/adapter/out/persistence/ProfilEntity.java`, `$RM/infrastructure/adapter/out/persistence/ProfilJpaAdapter.java`
- Delete: `$RMT/domain/ProfilTest.java`, `$RMT/application/ProfilServiceTest.java`, `$RMT/application/CreateProfilUseCaseTest.java`, `$RMT/infrastructure/adapter/in/web/ProfilControllerIT.java`, `$RMT/infrastructure/adapter/out/persistence/ProfilJpaAdapterTest.java`
- Test: `$RMT/infrastructure/adapter/in/web/RoleControllerIT.java`
- Modify: `$RMT/infrastructure/adapter/in/kafka/PlayerRegisteredFlowIT.java` (assertions sur `RoleAssignmentPort`)

**Interfaces:**
- Produces: `RoleController` `@RequestMapping("/api/roles")` :
  `GET /{playerId}` (`@Transactional(readOnly=true)`) → 200 DTO / 404 ;
  `PUT /{playerId}` (`@Transactional`, `@Valid @RequestBody
  ChangeRoleRequest`) → 200 DTO / 404 / 400 (rôle inconnu → Jackson
  `HttpMessageNotReadableException` → 400 par défaut). Pas de `POST`.
- Produces: `SecurityConfig` :
  `.requestMatchers("/actuator/**").permitAll()`,
  `.requestMatchers(HttpMethod.PUT, "/api/roles/**").hasRole("ADMIN")`,
  `.requestMatchers("/api/**").authenticated()`, `.anyRequest().permitAll()`.
  Utilisateurs in-memory `ali/password123` (USER) et `admin/admin123`
  (ADMIN) conservés.
- Produces: `KafkaTopicConfig` : `ACCESS_ROLE_ASSIGNED_TOPIC =
  "access.role.assigned"` + bean `NewTopic` (3 partitions, 1 réplica,
  rétention 7 j) ; `PROFIL_CREATED_TOPIC` et son bean supprimés ;
  `TELEMETRY_PLAYER_ACTION_TOPIC`, `PLAYERS_REGISTERED_TOPIC`,
  `PLAYERS_REGISTERED_DLT_TOPIC` inchangés.
- Produces: `KafkaConsumerConfig` : seule la factory
  `playerRegisteredKafkaListenerContainerFactory` subsiste
  (`profilEventKafkaListenerContainerFactory` supprimée avec
  `ProfilEventConsumer`).

- [x] **Step 1: `RoleControllerIT` qui échoue** (modèle
  `ProfilControllerIT`, seed via `RoleAssignmentPort`) : GET 200 avec
  `$.role == "PLAYER"` ; GET 404 ; PUT en `admin` → 200 `$.role ==
  "MODERATOR"` ; PUT en `ali` → 403 ; PUT rôle inconnu `{"role":"KING"}`
  en `admin` → 400 ; POST `/api/roles/uuid-1` → 405 ; sans auth → 401.

- [x] **Step 2: Implémenter `RoleController`, adapter `SecurityConfig`,
  `KafkaTopicConfig`, `UseCaseConfig`** (beans `AssignDefaultRoleUseCase`,
  `ChangeRoleUseCase`, `RoleAssignmentQueryService` ; beans profil
  retirés), rebrancher `PlayerRegisteredConsumer` sur
  `AssignDefaultRoleUseCase`.

- [x] **Step 3: Supprimer le domaine profil** (`git rm` des fichiers
  listés), retirer la seconde factory de `KafkaConsumerConfig`.

- [x] **Step 4: Adapter `PlayerRegisteredFlowIT`** : injecter
  `RoleAssignmentPort`, `validEventCreatesRoleAssignment` attend
  `existsByPlayerId("uuid-ok")` ; le test DLT inchangé.

- [x] **Step 5: Vérifier**

Run: `cd service-role-manager && mvn test` → vert.
Run: `grep -rni --exclude-dir=target 'profil' service-role-manager/src` →
aucune ligne (les mentions restantes vivent dans `CLAUDE.md`, Task 12).

- [x] **Step 6: hexagonal-boundary-guard (bloquant) + spring-java-reviewer**
  (`@Transactional` sur le controller/consumer seulement ; `hasRole` vs
  `roles("ADMIN")` cohérents ; idempotence du consumer).

- [x] **Step 7: Commit**

```bash
git add -A service-role-manager/src
git commit -m "feat(role-manager): expose /api/roles, assign default role on registration, drop profil domain"
```

---

## Phase D — sso projette `access.role.assigned`

Préfixes : `SS=service-sso/src/main/java/com/nebula/sso`,
`SST=service-sso/src/test/java/com/nebula/sso`.

### Task 9: `ApplyRoleAssignmentUseCase` + `AccountPort.findById`

**Files:**
- Modify: `$SS/domain/port/out/AccountPort.java` (+ `Optional<Account> findById(String id)`)
- Modify: `$SS/infrastructure/adapter/out/persistence/AccountJpaAdapter.java` (implémente `findById` via `accountRepository.findById`)
- Create: `$SS/application/event/RoleAssignedEvent.java`
- Create: `$SS/application/ApplyRoleAssignmentUseCase.java`
- Test: `$SST/application/ApplyRoleAssignmentUseCaseTest.java` ; Modify: `$SST/infrastructure/adapter/out/persistence/AccountJpaAdapterTest.java` (+ cas `findById`)

**Interfaces:**
- Produces: `record RoleAssignedEvent(String eventId, int eventVersion,
  String occurredAt, String playerId, String role)` — copie locale du
  contrat v1 (Javadoc : « dupliqué volontairement depuis
  service-role-manager, pas de lib partagée »). `role` reste `String`
  côté sso (sso ne possède pas l'enum).
- Produces: `ApplyRoleAssignmentUseCase(AccountPort).execute(RoleAssignedEvent)` :
  `playerId` ou `role` null/blanc → `IllegalArgumentException` (→ DLT
  après retries) ; compte introuvable → log warn + return ; rôle identique
  → return (idempotence) ; sinon `account.setRole(role)` + `save`.

- [x] **Step 1: Tests unitaires qui échouent** : met à jour le rôle ;
  no-op si identique (`save` jamais appelé) ; ignore compte inconnu ;
  rejette `playerId`/`role` manquants. `AccountJpaAdapterTest` :
  `findById` présent/absent.

- [x] **Step 2: Implémenter** port, adapter, event, use case.

- [x] **Step 3: Vérifier** → `cd service-sso && mvn test` vert.

- [x] **Step 4: hexagonal-boundary-guard** (bloquant).

- [x] **Step 5: Commit**

```bash
git add service-sso/src
git commit -m "feat(sso): apply role assignments from access.role.assigned events"
```

---

### Task 10: Consumer Kafka `RoleAssignedConsumer` + DLT + test de flux

**Files:**
- Create: `$SS/infrastructure/adapter/in/kafka/RoleAssignedConsumer.java`
- Create: `$SS/infrastructure/config/KafkaConsumerConfig.java`
- Modify: `$SS/infrastructure/config/KafkaTopicConfig.java` (+ `ACCESS_ROLE_ASSIGNED_TOPIC`, `ACCESS_ROLE_ASSIGNED_DLT_TOPIC` + bean DLT 1 partition, rétention 14 j ; **pas** de bean pour `access.role.assigned` lui-même, possédé par role-manager)
- Modify: `$SS/infrastructure/config/UseCaseConfig.java` (bean `ApplyRoleAssignmentUseCase`)
- Modify: `service-sso/src/main/resources/application.properties` (+ `spring.kafka.consumer.group-id=sso`, `spring.kafka.consumer.auto-offset-reset=earliest`)
- Modify: `service-sso/src/test/resources/application.properties` (+ mêmes clés, `spring.kafka.listener.auto-startup=false`)
- Modify: `service-sso/pom.xml` (ajouter `spring-kafka-test` scope test s'il manque)
- Test: `$SST/infrastructure/adapter/in/kafka/RoleAssignedFlowIT.java`

**Interfaces:**
- Produces: `RoleAssignedConsumer` `@KafkaListener(topics =
  KafkaTopicConfig.ACCESS_ROLE_ASSIGNED_TOPIC, groupId = "sso",
  containerFactory = "roleAssignedKafkaListenerContainerFactory")`,
  `@Transactional`, délègue à `ApplyRoleAssignmentUseCase`.
- Produces: `KafkaConsumerConfig.roleAssignedKafkaListenerContainerFactory`
  : réplique de la factory `players.registered` de role-manager
  (`ErrorHandlingDeserializer` autour de `JsonDeserializer<RoleAssignedEvent>`
  ignorant les type headers — les messages viennent du router Debezium,
  sans `__TypeId__` ; `DeadLetterPublishingRecoverer` vers
  `access.role.assigned.dlt` partition 0 ; `ExponentialBackOff(500, 2.0)`,
  3 tentatives). Reprendre les deux commentaires explicatifs de l'original
  (blocage de partition sans `ErrorHandlingDeserializer`, DLT mono-partition).

- [x] **Step 1: Vérifier les dépendances de test**

`grep -n 'spring-kafka-test' service-sso/pom.xml` ; si absent, ajouter la
dépendance (scope `test`) à côté de `spring-boot-starter-test`.
Awaitility est fourni par `spring-boot-starter-test` (Boot ≥ 3.2).

- [x] **Step 2: `RoleAssignedFlowIT` qui échoue** (modèle
  `PlayerRegisteredFlowIT` : `@SpringBootTest @DirtiesContext
  @EmbeddedKafka(partitions=3, topics={"access.role.assigned",
  "access.role.assigned.dlt"})`, `auto-startup=true`) : seed un `Account`
  (`role=PLAYER`) via `AccountPort`, envoyer `RoleAssignedEvent(...,
  "MODERATOR")` → `await` jusqu'à `accountPort.findById(id).get().getRole()
  == "MODERATOR"` ; événement sans `playerId` → message sur le DLT.

- [x] **Step 3: Implémenter** consumer, configs, properties.

- [x] **Step 4: Vérifier** → `cd service-sso && mvn test` vert.

- [x] **Step 5: hexagonal-boundary-guard + spring-java-reviewer**
  (`@Transactional` sur le consumer ; factory correcte ; pas de bean
  `NewTopic` pour un topic non possédé).

- [x] **Step 6: Commit**

```bash
git add service-sso
git commit -m "feat(sso): consume access.role.assigned with retries and dead letter topic"
```

---

## Phase E — Outillage (JMeter, CI)

### Task 11: Plans JMeter vers `POST /auth/register`, Makefile, CI

**Files:**
- Move: `service-load-testing/jmeter/test-plans/profil-api-load-test.jmx` → `auth-register-load-test.jmx`
- Move: `service-load-testing/jmeter/test-plans/profil-api-stress-test.jmx` → `auth-register-stress-test.jmx`
- Modify: `service-load-testing/jmeter/test-plans/telemetry-stress-test.jmx` (host par défaut `role-manager`)
- Modify: `service-load-testing/jmeter/run-test.sh` (plan par défaut)
- Modify: `Makefile` (`load-test`, `stress-test`)
- Modify: `docker-compose.yml` (`jmeter.environment.TEST_PLAN` défaut, `depends_on` + `sso`)
- Modify: `.github/workflows/performance-tests.yml`

**Interfaces:**
- Produces: plans `auth-register-*` : host `${__P(host,sso)}`, port
  `${__P(port,8082)}`, sampler `POST /auth/register`, body JSON
  `{"username":"<unique>","email":"<unique>@example.com","password":"password123","region":"EU"}`,
  **sans** header `Authorization` (`/auth/**` est `permitAll`), assertion
  **201**. Labels InfluxDB : `application=nebula`, `testTitle=Auth Register
  Load Test` / `Stress Test`.

- [x] **Step 1: Lire les contraintes de `RegisterRequest`**

`cat service-sso/src/main/java/com/nebula/sso/application/dto/RegisterRequest.java`
→ noter les `@Size`/`@Pattern` sur `username`/`email`. Choisir un
générateur d'unicité compatible (par défaut :
`lt${__threadNum}${__time()}${__Random(100,999)}`, ≤ 20 caractères ; si
un `@Pattern` interdit les chiffres en tête, préfixer d'une lettre — déjà
le cas).

- [x] **Step 2: Renommer et éditer les plans**

```bash
git mv service-load-testing/jmeter/test-plans/profil-api-load-test.jmx service-load-testing/jmeter/test-plans/auth-register-load-test.jmx
git mv service-load-testing/jmeter/test-plans/profil-api-stress-test.jmx service-load-testing/jmeter/test-plans/auth-register-stress-test.jmx
```

Dans les deux fichiers : `testname="Profil API - ..."` → `"Auth Register
- ..."` ; `${__P(host,app)}` → `${__P(host,sso)}` ; `${__P(port,8080)}` →
`${__P(port,8082)}` ; chaque sampler `POST /api/profils` → `POST
/auth/register` (`testname`, `HTTPSampler.path`, commentaire XML, message
d'assertion) ; body → JSON ci-dessus ; supprimer l'`elementProp`
`Authorization` du `HeaderManager` (garder `Content-Type`) ; assertion
`200` → `201` ; `Argument.value` `MaDemo` → `nebula`, `Profil API Load
Test` → `Auth Register Load Test` (idem stress). Dans
`telemetry-stress-test.jmx` : `${__P(host,app)}` → `${__P(host,role-manager)}`
et `application` `MaDemo` → `nebula`.

- [x] **Step 3: run-test.sh, Makefile, compose**

`run-test.sh` : défaut `auth-register-load-test.jmx` (commentaire
inclus). `Makefile` : `load-test` (défaut, aide « POST /auth/register, 50
users / 60s »), `stress-test: TEST_PLAN=auth-register-stress-test.jmx ...`.
`docker-compose.yml` : `TEST_PLAN: ${TEST_PLAN:-auth-register-load-test.jmx}`,
`jmeter.depends_on` : `role-manager`, `sso`, `influxdb`.

- [x] **Step 4: Workflow CI**

`performance-tests.yml` : options `auth-register-load-test` /
`auth-register-stress-test` (défaut load) ; `paths` :
`service-sso/src/**`, `service-role-manager/src/**`,
`service-load-testing/jmeter/test-plans/**` ; `docker compose up -d
--build sso role-manager db kafka kafka-connect kafka-connect-init
prometheus grafana alertmanager` ; attente santé sur
`http://localhost:8082/actuator/health` **et** `:8080` (logs
`docker compose logs sso role-manager` en échec) ; `-Jhost=sso
-Jport=8082` ; chemins d'artefacts et de JTL
`service-load-testing/jmeter/...` (les chemins `load-testing/` actuels sont
obsolètes depuis le plan 1).

- [x] **Step 5: Vérifier**

Run: `docker compose config -q && echo OK`.
Run: `grep -rn -e 'profil' -e 'MaDemo' -e '\bapp\b' service-load-testing Makefile docker-compose.yml .github` → aucune ligne.
Optionnel (stack up) : `make load-test` → 0 assertion en échec.

- [x] **Step 6: Commit**

```bash
git add service-load-testing Makefile docker-compose.yml .github/workflows/performance-tests.yml
git commit -m "chore(load-testing): retarget JMeter plans to POST /auth/register and rename services"
```

---

## Phase F — Documentation

### Task 12: Docs adjacentes au code (CLAUDE.md, règles, agents, skills)

**Files:**
- Modify: `service-sso/CLAUDE.md` (titre, package `com.nebula.sso`, structure : + `application.ApplyRoleAssignmentUseCase`, `application.event.RoleAssignedEvent`, `infrastructure.adapter.in.kafka.RoleAssignedConsumer`, `infrastructure.config.KafkaConsumerConfig` ; commande `mvn test` depuis `service-sso`)
- Modify: `service-role-manager/CLAUDE.md` (réécrit : service RBAC, package `com.nebula.rolemanager` — dette `com.example` soldée —, structure de la Phase C, télémétrie conservée avec justification, commande locale)
- Modify: `.claude/rules/hexagonal-architecture.md` (section « État actuel de la migration » : noms de modules, packages, classes ; référence à ce plan)
- Modify: `CLAUDE.md` racine (titre, Repo Map : `service-sso/` — auth, JWT RS256, `com.nebula.sso` ; `service-role-manager/` — RBAC, `com.nebula.rolemanager` ; Commandes : `mvn -pl service-sso test`, `mvn -pl service-role-manager test`)
- Modify: `.claude/agents/hexagonal-boundary-guard.md`, `.claude/skills/scaffold-hexagonal-port/SKILL.md` (noms de services et exemple de package `com.nebula.rolemanager.domain.port.out`), `AGENTS.md` si mention

**Interfaces:** aucune — documentation.

- [x] **Step 1: Éditer** les fichiers listés (contenu court, factuel,
  aligné sur l'état réel du code après Task 11).

- [x] **Step 2: Vérifier zéro-résidu global**

Run (commande des Global Constraints) → aucune ligne hors
`docs/superpowers/` et `documents/`.

- [x] **Step 3: Commit**

```bash
git add CLAUDE.md AGENTS.md .claude/rules .claude/agents .claude/skills service-sso/CLAUDE.md service-role-manager/CLAUDE.md
git commit -m "docs: update agent guidance for service-sso and service-role-manager"
```

---

### Task 13: docs-sync — `documents/ARCHITECTURE.md` et `README.md`

**Files:**
- Modify: `documents/ARCHITECTURE.md`
- Modify: `README.md`

**Interfaces:** aucune — documentation.

- [x] **Step 1: Invoquer l'agent `docs-sync`** sur le diff cumulé des
  Tasks 1-11 avec les attentes suivantes :
  - §1 (tableau « Brique / État ») : événements implémentés =
    `players.registered`, `access.role.assigned`.
  - §4 : la ligne « Identité & Accès » devient deux lignes —
    « Authentification (SSO) » (comptes, credentials, JWT ; publie
    `players.registered` ; `service-sso`) et « Accès & Rôles (RBAC) »
    (attribution des rôles ; publie `access.role.assigned` ;
    `service-role-manager`) ; « Joueurs & Profils » marqué « cible, non
    implémenté dans la démo ».
  - §5 : exemples `Profil`/`ProfilDto`, `ProfilEventProducer` remplacés
    par `RoleAssignment`/`RoleAssignmentDto` et par la chorégraphie
    sso ↔ role-manager (spec §3.5).
  - §9 : catalogue = `players.registered` (playerId, 3, 7 j) +
    `access.role.assigned` (playerId, 3, 7 j) + `.dlt` ; suppression de
    `players.profil.created`.
  - §10 : « RBAC — rôles maîtrisés par `service-role-manager`
    (`PUT /api/roles/{playerId}`, admin), projetés dans `service-sso`
    via `access.role.assigned` et émis dans le claim `role` du JWT.
    Cohérence à terme : un login entre le changement et sa projection
    émet encore l'ancien rôle (latence CDC ≈ centaines de ms). Basic
    in-memory de role-manager = raccourci de démo. »
  - §11 : `RegisterUseCase.execute()` (service-sso),
    `AssignDefaultRoleUseCase.execute()` / `ChangeRoleUseCase.execute()`
    (service-role-manager).
  - §13 : métrique métier exemple `roles.assigned.total`.
  - §15 : `ProfilEventProducer` → outbox + Debezium.
  - README : titre, sections « Flux inscription », endpoints
    (`sso:8082 /auth/*`, `role-manager:8080 /api/roles/*`, `/api/telemetry`),
    tableau des topics, schéma ASCII (`app` → `role-manager`, + `sso`),
    commandes JMeter (`auth-register-*`), chemins `service-load-testing/`,
    mention Prometheus (`role-manager:8080`, `sso:8082`).

- [x] **Step 2: Revue rapide du diff proposé** (aucune section
  « cible » réécrite au-delà du nécessaire ; pas de PII dans les exemples
  d'événements).

- [x] **Step 3: Commit séparé**

```bash
git add documents/ARCHITECTURE.md README.md
git commit -m "docs: describe sso and role-manager split with access.role.assigned flow"
```

---

## Phase G — Vérification globale

### Task 14: Vérification e2e

**Files:** aucun changement de code attendu.

**Interfaces:** aucune — vérification pure.

- [x] **Step 1: Stack et connecteurs**

Invoquer `e2e-verifier` (ou skill `e2e-verify`) : `make setup` ;
`curl -s localhost:8083/connectors` liste `sso-outbox-connector` et
`role-manager-outbox-connector` (état `RUNNING` sur
`/connectors/<name>/status`).

- [x] **Step 2: Scénario nominal**

1. `POST localhost:8082/auth/register` → 201, décoder le JWT : claim
   `role=PLAYER`.
2. AKHQ (`localhost:8081`) : message sur `players.registered` puis sur
   `access.role.assigned` (`"role":"PLAYER"`, clé = playerId, pas d'email).
3. `GET localhost:8080/api/roles/{playerId}` (Basic `ali`) → 200 `PLAYER`.
4. `PUT localhost:8080/api/roles/{playerId}` (Basic `admin`, body
   `{"role":"MODERATOR"}`) → 200 ; en `ali` → 403.
5. Nouveau message `access.role.assigned` (`MODERATOR`).
6. `POST /auth/login` → JWT `role=MODERATOR` (retenter jusqu'à ~2 s : CDC).
7. `make load-test` → 0 échec d'assertion ; `make telemetry-test` → OK.
8. `make logs` sans boucle d'erreur (pas de rejeu serré Kafka, cf.
   commit 2e9586b) ; `make down` propre.

- [x] **Step 3: Correctif si nécessaire**

Régression → commit `fix:` dédié, rejouer le scénario. Tout conforme →
ne rien committer : condition de clôture du plan.
