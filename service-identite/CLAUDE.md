@../.claude/rules/hexagonal-architecture.md

# service-identite

Service auth : inscription/connexion, émission de JWT RS256, publication de
`players.registered` via Outbox+Debezium. Package racine :
`com.nebula.identite`. Possède sa propre base (schéma dédié).

## Structure actuelle (hexagonale)

Migration effectuée (plan
`docs/superpowers/plans/2026-09-18-plan-2-hexagonal-migration.md`, Tasks
8-13).

- `com.nebula.identite.domain` — `Account`, `OutboxEventToPublish`,
  `domain.port.out.AccountPort`, `TokenPort`, `PasswordHasherPort`,
  `EventPublisherPort`
- `com.nebula.identite.application` — `RegisterUseCase`, `LoginUseCase`,
  DTO de frontière (`application.dto`), `PlayerRegisteredEvent`
  (`application.event`)
- `com.nebula.identite.infrastructure.adapter.in.web` — `AuthController`
- `com.nebula.identite.infrastructure.adapter.out.persistence` —
  `AccountEntity`, `AccountRepository`, `AccountJpaAdapter` (implémente
  `AccountPort`)
- `com.nebula.identite.infrastructure.adapter.out.security` —
  `JwtTokenAdapter` (implémente `TokenPort`), `BCryptPasswordHasherAdapter`
  (implémente `PasswordHasherPort`)
- `com.nebula.identite.infrastructure.adapter.out.outbox` — `OutboxEvent`,
  `OutboxEventRepository`, `OutboxEventPublisherAdapter` (implémente
  `EventPublisherPort`), `OutboxCleanupJob`
- `com.nebula.identite.infrastructure.config` — configuration Spring
  (sécurité, JWT, Kafka, câblage des use cases)

## Commandes locales

`mvn -pl service-identite test`

## Particularités

Clés RSA dev committées, préfixées `dev-` (raccourci de projet école,
documenté). Ne pas régénérer sans mettre à jour
`scripts/generate-dev-jwt-keys.sh` et les deux fichiers `keys/dev-*.pem`.
