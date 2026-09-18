@../.claude/rules/hexagonal-architecture.md

# service-profil

Service profils joueurs : création réactive sur `players.registered`,
lecture/mise à jour REST, télémétrie. Package racine : `com.example` (dette
technique existante — ne pas renommer silencieusement, seulement via une
tâche de plan dédiée).

## Structure actuelle (hexagonale)

Migration effectuée (plan
`docs/superpowers/plans/2026-09-18-plan-2-hexagonal-migration.md`).

- `com.example.domain` — `Profil`, `OutboxEventToPublish`,
  `domain.port.out.ProfilPort`, `domain.port.out.EventPublisherPort`
- `com.example.application` — `ProfilService`, `CreateProfilUseCase`,
  `ProfilMapper`, DTO de frontière (`ProfilDto`, `UpdateProfilRequest`,
  `TelemetryEventDto`)
- `com.example.infrastructure.adapter.in.web` — `ProfilController`,
  `TelemetryController`
- `com.example.infrastructure.adapter.in.kafka` — `PlayerRegisteredConsumer`,
  `ProfilEventConsumer`, `TelemetryEventConsumer`
- `com.example.infrastructure.adapter.out.persistence` — `ProfilEntity`,
  `ProfilJpaAdapter` (implémente `ProfilPort`)
- `com.example.infrastructure.adapter.out.outbox` — `OutboxEvent`,
  `OutboxEventRepository`, `OutboxEventPublisherAdapter` (implémente
  `EventPublisherPort`), `OutboxCleanupJob`
- `com.example.infrastructure.adapter.out.kafka` — `TelemetryEventProducer`
- `com.example.infrastructure.config` — configuration Spring (Kafka,
  sécurité, câblage des use cases)

## Commandes locales

`mvn -pl service-profil test`
