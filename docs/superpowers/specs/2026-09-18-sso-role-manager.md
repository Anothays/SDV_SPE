# Design — Renommage service-identite → service-sso, service-profil → service-role-manager (RBAC)

Date : 2026-09-18
Statut : validé (brainstorming en session — trois décisions tranchées par
l'utilisateur, cf. §2)
Sujet : `documents/ARCHITECTURE.md` §4/§5/§9/§10/§11,
`.claude/rules/hexagonal-architecture.md`

## 1. Besoin

Les noms `service-identite` et `service-profil` sont ambigus : les deux
évoquent « qui est l'utilisateur ». En réalité `service-identite` fait de
l'**authentification** (inscription, connexion, émission de JWT RS256) et
`service-profil` porte un domaine **joueur** (`username`, `region`,
`level`) créé en réaction à `players.registered`.

Décision utilisateur : deux responsabilités clairement nommées et
séparées.

- `service-identite` → **`service-sso`** : authentification uniquement.
- `service-profil` → **`service-role-manager`** : administration des
  rôles des utilisateurs (RBAC). Le domaine « profil joueur » (`level`,
  `region`) est **abandonné** : ce n'est pas un renommage, c'est un
  changement de domaine.

Ce découpage matérialise la séparation classique
authentification (qui es-tu) / autorisation (que peux-tu faire), tout en
conservant le flux démo central du repo : écriture métier + outbox dans la
même transaction → Debezium CDC → Kafka → consommateur idempotent.

## 2. Décisions tranchées (brainstorming)

| Question | Décision | Alternative écartée |
|---|---|---|
| Devenir de `service-profil` | Transformé en `service-role-manager` ; domaine profil supprimé | Garder profil + ajouter un 3e service (plus de code, sans valeur pour la démo) |
| Comment `sso` connaît le rôle à mettre dans le JWT | **Asynchrone** : `role-manager` publie `access.role.assigned` (outbox), `sso` projette le rôle dans sa table `account` | Appel synchrone au login (couplage temporel, contraire au §5) ; rôle hors JWT (chaque service interroge role-manager) |
| Portée du renommage | **Complète** : dossiers, compose, connecteurs Debezium, schémas MySQL, packages Java (`com.nebula.sso`, `com.nebula.rolemanager`) | Dossiers + infra seulement, packages plus tard |

## 3. Cible

### 3.1 Nommage

| Avant | Après |
|---|---|
| `service-identite/` | `service-sso/` |
| `com.nebula.identite` | `com.nebula.sso` |
| artifactId `service-identite`, `spring.application.name=service-identite` | `service-sso` |
| compose `identite` (container `identite`), port 8082 | compose `sso` (container `sso`), port 8082 inchangé |
| base MySQL `identite` | base MySQL `sso` |
| `identite-outbox-connector.json` (`topic.prefix=identite`, `schema-history.identite`) | `sso-outbox-connector.json` (`topic.prefix=sso`, `schema-history.sso`) |
| `service-profil/` | `service-role-manager/` |
| `com.example` (prod) et `com.example.MaDemo` (tests) | `com.nebula.rolemanager` (prod **et** tests — solde la dette `MaDemo`) |
| artifactId `MaDemo`, `spring.application.name=MaDemo` | `service-role-manager` |
| compose `app` (container `app`), port 8080 | compose `role-manager` (container `role-manager`), port 8080 inchangé |
| base MySQL `maBase` | base MySQL `role_manager` |
| `profil-outbox-connector.json` (`topic.prefix=profil`) | `role-manager-outbox-connector.json` (`topic.prefix=role-manager`, `schema-history.role-manager`) |

Les `database.server.id` Debezium (184055 / 184056) restent distincts et
inchangés. Aucune migration de données : la stack dev est éphémère
(MySQL sans volume nommé, schémas recréés à chaque `docker compose up`).

### 3.2 `service-sso` (ex-identite)

Renommage pur **plus** une nouvelle capacité entrante : consommer
`access.role.assigned` pour tenir à jour `Account.role`.

- `domain.Account` inchangé (`role` reste un `String`, projection locale
  du rôle maître détenu par `role-manager`).
- `domain.port.out.AccountPort` gagne `findById(String)`.
- `application.ApplyRoleAssignmentUseCase.execute(RoleAssignedEvent)` :
  compte introuvable → log warn + ignoré (pas de retry possible :
  `role-manager` ne crée une attribution qu'à partir d'un
  `players.registered` émis par `sso` après commit du compte) ; rôle
  identique → no-op (idempotence) ; sinon `account.setRole(...)` + save.
- `application.event.RoleAssignedEvent` : copie locale du contrat (pas de
  lib partagée, même règle que `PlayerRegisteredEvent` côté profil).
- `infrastructure.adapter.in.kafka.RoleAssignedConsumer`
  (`@KafkaListener`, groupe `sso`, `@Transactional`), factory dédiée avec
  `ErrorHandlingDeserializer` + retries + DLT `access.role.assigned.dlt`
  (réplique de `KafkaConsumerConfig` de service-profil).
- `RegisterUseCase` continue de poser `role = "PLAYER"` par défaut : le JWT
  émis à l'inscription porte un rôle immédiatement, sans attendre la
  boucle asynchrone. `role-manager` confirme ensuite `PLAYER` via
  événement (no-op côté sso).
- JWT, `LoginUseCase`, `AuthController`, clés `dev-*` : inchangés.

### 3.3 `service-role-manager` (ex-profil)

Nouveau domaine **RBAC**, un rôle par joueur (modèle simple aligné sur le
claim `role` scalaire du JWT).

- `domain.RoleAssignment` : `playerId`, `role` (enum `domain.Role` :
  `PLAYER`, `MODERATOR`, `ADMIN` — les trois rôles du §10),
  `assignedAt`, `updatedAt`. POJO sans annotation.
- `domain.port.out.RoleAssignmentPort` : `save`, `findByPlayerId`,
  `existsByPlayerId`. `domain.port.out.EventPublisherPort` conservé.
- `application.AssignDefaultRoleUseCase.execute(PlayerRegisteredEvent)` :
  remplace `CreateProfilUseCase`. `playerId` manquant → exception (→ DLT
  après retries, comportement actuel) ; attribution déjà existante →
  ignoré (idempotence) ; sinon crée `PLAYER` et publie
  `access.role.assigned` via outbox **dans la même transaction**.
- `application.ChangeRoleUseCase.execute(playerId, Role)` : attribution
  introuvable → `RoleAssignmentNotFoundException` (404) ; met à jour et
  publie `access.role.assigned`.
- `application.RoleAssignmentQueryService.findByPlayerId` (lecture).
- `application.dto.RoleAssignmentDto` (`playerId`, `role`, `assignedAt`,
  `updatedAt`), `application.dto.ChangeRoleRequest` (`role`, `@NotNull`).
- REST `infrastructure.adapter.in.web.RoleController` :
  - `GET /api/roles/{playerId}` → 200 `RoleAssignmentDto` / 404
  - `PUT /api/roles/{playerId}` body `{"role":"MODERATOR"}` → 200 / 404 /
    400 (rôle inconnu)
  - Pas de `POST` : une attribution naît par `players.registered`
    uniquement (même principe que l'ancien profil).
- Sécurité : HTTP Basic + utilisateurs en mémoire **conservés** (raccourci
  de démo déjà assumé au §10). Nouveauté : `PUT /api/roles/**` exige le
  rôle `ADMIN` (`admin/admin123`), `GET` exige d'être authentifié.
  La validation du JWT `sso` par `role-manager` est hors périmètre (§5).
- Kafka : `KafkaTopicConfig` déclare `access.role.assigned` (clé
  `playerId`, 3 partitions, rétention 7 j) et garde
  `players.registered.dlt` + `telemetry.player.action`. Le
  self-consumer `ProfilEventConsumer` disparaît : le consommateur réel de
  l'événement est désormais `sso`.
- Télémétrie (`TelemetryController`, `TelemetryEventProducer`,
  `TelemetryEventConsumer`, `TelemetryEventDto`) **conservée telle
  quelle** : brique transverse d'observabilité (§13) utilisée par
  `make telemetry-test` et les dashboards Grafana. Impureté de domaine
  assumée et documentée ; extraction en `service-telemetry` = suivi
  ultérieur (§5).
- Supprimés : `Profil`, `ProfilPort`, `ProfilEntity`, `ProfilJpaAdapter`,
  `ProfilService`, `ProfilMapper`, `CreateProfilUseCase`, `ProfilDto`,
  `UpdateProfilRequest`, `ProfilController`, `ProfilEventConsumer`,
  `ProfilNotFoundException` et leurs tests.

### 3.4 Contrat d'événement `access.role.assigned` v1

Convention §9 `domaine.entité.action` — domaine « Accès ».

```json
{
  "eventId": "uuid",
  "eventVersion": 1,
  "occurredAt": "2026-09-18T10:00:00Z",
  "playerId": "uuid du compte",
  "role": "PLAYER | MODERATOR | ADMIN"
}
```

- Producteur : `role-manager` (outbox : `aggregatetype=access.role.assigned`,
  `aggregateid=playerId`, `type=RoleAssigned`, `payload` = JSON ci-dessus).
- Consommateur : `sso` (groupe `sso`, DLT `access.role.assigned.dlt`
  déclaré par le consommateur, comme `players.registered.dlt` l'est
  aujourd'hui par profil).
- Aucune PII (pas d'email, pas d'username — `sso` retrouve le compte par
  `playerId`).
- `players.registered` v1 inchangé.

### 3.5 Boucle complète (chorégraphie)

```
POST /auth/register (sso)
  ├─ tx : account(role=PLAYER) + outbox(players.registered)
  └─ 201 + JWT{role=PLAYER}
Debezium(sso) → players.registered
  → role-manager : tx : role_assignment(PLAYER) + outbox(access.role.assigned)
Debezium(role-manager) → access.role.assigned
  → sso : account.role déjà PLAYER → no-op

PUT /api/roles/{id} {"role":"MODERATOR"} (role-manager, admin)
  └─ tx : role_assignment(MODERATOR) + outbox(access.role.assigned)
Debezium(role-manager) → access.role.assigned
  → sso : account.role = MODERATOR
POST /auth/login → JWT{role=MODERATOR}
```

Cohérence à terme (§11) : entre le `PUT` et la projection côté `sso`, un
login émet encore l'ancien rôle. Latence attendue : quelques centaines de
ms (CDC). Documenté au §10.

### 3.6 Infra & outillage

- `docker-compose.yml` : services `sso` et `role-manager` ; `db` :
  `MYSQL_DATABASE=role_manager`, init `create-sso-db.sql` ; `depends_on`
  de `prometheus` et `jmeter` mis à jour.
- `service-messaging/mysql-init/create-identite-db.sql` →
  `create-sso-db.sql`.
- Prometheus : deux jobs (`role-manager` → `role-manager:8080`, `sso` →
  `sso:8082`) ; règle `AppDown` adaptée (`job=~"role-manager|sso"`).
  `sso` n'était pas scrapé jusqu'ici : ajout d'une ligne, conforme au
  §18 (« ajouter une cible = une ligne »).
- `Makefile` : `logs` suit `role-manager` et `sso` ; cibles de tests de
  charge renommées.
- JMeter : les plans `profil-api-*` visent `POST /api/profils`, endpoint
  **déjà supprimé** au plan 1 (405 → les assertions 200 échouent
  aujourd'hui). Ils sont remplacés par `auth-register-load-test.jmx` /
  `auth-register-stress-test.jmx` ciblant `POST /auth/register` sur
  `sso:8082` (username/email aléatoires) : une requête exerce toute la
  chaîne sso → outbox → Debezium → role-manager. `telemetry-stress-test.jmx`
  inchangé (host `role-manager`).
- `.github/workflows/performance-tests.yml` : chemins et noms de services
  compose mis à jour (les chemins actuels `profil-service/`,
  `load-testing/` sont déjà obsolètes).
- Docs : `documents/ARCHITECTURE.md` (§4 : la ligne « Identité & Accès »
  devient deux domaines « Authentification (SSO) » et « Accès & Rôles
  (RBAC) », « Joueurs & Profils » repasse en cible non implémentée ; §9 :
  catalogue avec `players.registered` + `access.role.assigned` ; §10 :
  role-manager source de vérité, sso projette dans le JWT ; §11 : noms des
  use cases), `README.md`, `.claude/rules/hexagonal-architecture.md`
  (section « État actuel »), `CLAUDE.md` racine (Repo Map, commandes),
  `CLAUDE.md` des deux services, mentions dans `.claude/agents/*.md`,
  `.claude/skills/*/SKILL.md`, `AGENTS.md`. Commit `docs:` séparé via
  l'agent `docs-sync`.

## 4. Périmètre

Dans le périmètre : tout le §3.

Hors périmètre :
- Validation du JWT `sso` par `role-manager` (Basic in-memory conservé).
- Multi-rôles par joueur, permissions fines, hiérarchie de rôles.
- Refresh tokens, révocation, expiration courte (15 min) du §10.
- Extraction de la télémétrie dans un service dédié.
- Lib partagée de contrats d'événements (duplication volontaire
  maintenue).
- Migration de données (stack éphémère).
- Modification du contrat `players.registered`.

## 5. Suites possibles (non planifiées)

- `service-role-manager` valide le JWT RS256 de `sso` (clé publique
  `dev-public.pem`) et remplace le Basic in-memory.
- `service-telemetry` : sortir `TelemetryController`/`TelemetryEventProducer`/
  `TelemetryEventConsumer` de `role-manager`.
- Gateway (§6/§10) portant l'authentification JWT gros grain.

## 6. Critères de succès

- `mvn -pl service-sso test` et `mvn -pl service-role-manager test`
  verts à chaque tâche ; plus aucune référence à `com.example`,
  `com.nebula.identite`, `MaDemo`, `maBase`, `identite` (hors historique
  git et anciens specs/plans datés, qui restent tels quels).
- `hexagonal-boundary-guard` : aucun import interdit dans `domain` /
  `application` des deux services.
- `docker compose config -q` OK ; `make setup` démarre `sso`,
  `role-manager`, les deux connecteurs Debezium enregistrés
  (`curl kafka-connect:8083/connectors` liste `sso-outbox-connector` et
  `role-manager-outbox-connector`).
- Vérification e2e (`e2e-verifier`) : `POST /auth/register` → JWT avec
  `role=PLAYER` → `access.role.assigned` visible dans AKHQ →
  `GET /api/roles/{id}` = `PLAYER` → `PUT /api/roles/{id}` `MODERATOR`
  (admin) → nouvel événement → `POST /auth/login` renvoie un JWT avec
  `role=MODERATOR`.
- `make load-test`, `make stress-test`, `make telemetry-test`
  s'exécutent sans assertion en échec.
- `ARCHITECTURE.md`, `README.md`, règles et `CLAUDE.md` reflètent le
  nouvel état (commit `docs:` séparé).
