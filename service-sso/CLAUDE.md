@../.claude/rules/hexagonal-architecture.md

# service-sso

Service d'authentification (SSO) : inscription/connexion, émission de JWT
RS256, publication de `players.registered` via Outbox+Debezium. Projette
le rôle maître de `service-role-manager` (événement `access.role.assigned`)
dans `account.role` pour l'émettre dans le claim `role` du JWT. Package
racine : `com.nebula.sso`. Possède sa propre base (schéma `sso`).

## Structure actuelle (hexagonale)

Plans `docs/superpowers/plans/2026-09-18-plan-2-hexagonal-migration.md`
(Tasks 8-13) puis `2026-09-18-plan-3-sso-role-manager.md` (Tasks 1-2,
9-10).

- `com.nebula.sso.domain` — `Account`, `OutboxEventToPublish`,
  `domain.port.out.AccountPort`, `TokenPort`, `PasswordHasherPort`,
  `EventPublisherPort`
- `com.nebula.sso.application` — `RegisterUseCase`, `LoginUseCase`,
  `ApplyRoleAssignmentUseCase`, DTO de frontière (`application.dto`),
  contrats d'événements `PlayerRegisteredEvent` (produit) et
  `RoleAssignedEvent` (consommé, copie locale) dans `application.event`
- `com.nebula.sso.infrastructure.adapter.in.web` — `AuthController`
- `com.nebula.sso.infrastructure.adapter.in.kafka` — `RoleAssignedConsumer`
  (groupe `sso`, retries + DLT `access.role.assigned.dlt`)
- `com.nebula.sso.infrastructure.adapter.out.persistence` —
  `AccountEntity`, `AccountRepository`, `AccountJpaAdapter` (implémente
  `AccountPort`)
- `com.nebula.sso.infrastructure.adapter.out.security` —
  `JwtTokenAdapter` (implémente `TokenPort`), `BCryptPasswordHasherAdapter`
  (implémente `PasswordHasherPort`)
- `com.nebula.sso.infrastructure.adapter.out.outbox` — `OutboxEvent`,
  `OutboxEventRepository`, `OutboxEventPublisherAdapter` (implémente
  `EventPublisherPort`), `OutboxCleanupJob`
- `com.nebula.sso.infrastructure.config` — configuration Spring
  (sécurité, JWT, topics et consumer Kafka, câblage des use cases)

## Commandes locales

Depuis `service-sso/` : `./mvnw test` (ou `mvn -pl service-sso test`
depuis la racine).

## Particularités

Clés RSA dev committées, préfixées `dev-` (raccourci de projet école,
documenté). Ne pas régénérer sans mettre à jour
`scripts/generate-dev-jwt-keys.sh` et les deux fichiers `keys/dev-*.pem`.

`RegisterUseCase` pose `role = "PLAYER"` par défaut pour que le JWT
d'inscription porte un rôle immédiatement ; `role-manager` confirme ensuite
via `access.role.assigned` (no-op idempotent). Cohérence à terme : entre un
changement de rôle et sa projection, un login émet encore l'ancien rôle.
