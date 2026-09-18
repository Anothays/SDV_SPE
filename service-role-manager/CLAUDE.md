@../.claude/rules/hexagonal-architecture.md

# service-profil

Service profils joueurs : création réactive sur `players.registered`,
lecture/mise à jour REST, télémétrie. Package racine : `com.nebula.rolemanager` (dette
technique existante — ne pas renommer silencieusement, seulement via une
tâche de plan dédiée).

## Structure actuelle (hexagonale)

Migration effectuée (plan
`docs/superpowers/plans/2026-09-18-plan-2-hexagonal-migration.md`).

- `com.nebula.rolemanager.domain` — `Profil`, `OutboxEventToPublish`,
  `domain.port.out.ProfilPort`, `domain.port.out.EventPublisherPort`
- `com.nebula.rolemanager.application` — `ProfilService`, `CreateProfilUseCase`,
  `ProfilMapper`, DTO de frontière (`ProfilDto`, `UpdateProfilRequest`,
  `TelemetryEventDto`)
- `com.nebula.rolemanager.infrastructure.adapter.in.web` — `ProfilController`,
  `TelemetryController`
- `com.nebula.rolemanager.infrastructure.adapter.in.kafka` — `PlayerRegisteredConsumer`,
  `ProfilEventConsumer`, `TelemetryEventConsumer`
- `com.nebula.rolemanager.infrastructure.adapter.out.persistence` — `ProfilEntity`,
  `ProfilJpaAdapter` (implémente `ProfilPort`)
- `com.nebula.rolemanager.infrastructure.adapter.out.outbox` — `OutboxEvent`,
  `OutboxEventRepository`, `OutboxEventPublisherAdapter` (implémente
  `EventPublisherPort`), `OutboxCleanupJob`
- `com.nebula.rolemanager.infrastructure.adapter.out.kafka` — `TelemetryEventProducer`
- `com.nebula.rolemanager.infrastructure.config` — configuration Spring (Kafka,
  sécurité, câblage des use cases)

## Commandes locales

`mvn -pl service-profil test`
