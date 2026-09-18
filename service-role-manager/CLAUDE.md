@../.claude/rules/hexagonal-architecture.md

# service-role-manager

Service RBAC : un rôle (`PLAYER`, `MODERATOR`, `ADMIN`) par joueur,
attribué par défaut en réaction à `players.registered`, modifiable par un
administrateur via REST. Source de vérité des rôles : chaque attribution
publie `access.role.assigned` via Outbox+Debezium, consommé par
`service-sso`. Package racine : `com.nebula.rolemanager` (prod et tests —
la dette `com.example` / `com.example.MaDemo` est soldée). Base dédiée
`role_manager`.

## Structure actuelle (hexagonale)

Plans `docs/superpowers/plans/2026-09-18-plan-2-hexagonal-migration.md`
(Tasks 1-6) puis `2026-09-18-plan-3-sso-role-manager.md` (Tasks 3-8).

- `com.nebula.rolemanager.domain` — `Role` (enum), `RoleAssignment`,
  `OutboxEventToPublish`, `domain.port.out.RoleAssignmentPort`,
  `domain.port.out.EventPublisherPort`
- `com.nebula.rolemanager.application` — `AssignDefaultRoleUseCase`,
  `ChangeRoleUseCase`, `RoleAssignmentQueryService`,
  `RoleAssignmentMapper`, DTO de frontière (`RoleAssignmentDto`,
  `ChangeRoleRequest`, `TelemetryEventDto`), contrat produit
  `application.event.RoleAssignedEvent`
- `com.nebula.rolemanager.event` — `PlayerRegisteredEvent` (contrat
  consommé, copie locale)
- `com.nebula.rolemanager.infrastructure.adapter.in.web` —
  `RoleController` (`GET/PUT /api/roles/{playerId}`, `PUT` réservé à
  `ADMIN`), `TelemetryController`
- `com.nebula.rolemanager.infrastructure.adapter.in.kafka` —
  `PlayerRegisteredConsumer` (groupe `role-manager`, retries + DLT),
  `TelemetryEventConsumer`
- `com.nebula.rolemanager.infrastructure.adapter.out.persistence` —
  `RoleAssignmentEntity`, `RoleAssignmentRepository`,
  `RoleAssignmentJpaAdapter` (implémente `RoleAssignmentPort`)
- `com.nebula.rolemanager.infrastructure.adapter.out.outbox` —
  `OutboxEvent`, `OutboxEventRepository`, `OutboxEventPublisherAdapter`
  (implémente `EventPublisherPort`), `OutboxCleanupJob`
- `com.nebula.rolemanager.infrastructure.adapter.out.kafka` —
  `TelemetryEventProducer`
- `com.nebula.rolemanager.infrastructure.config` — configuration Spring
  (Kafka, sécurité HTTP Basic in-memory, câblage des use cases)

## Commandes locales

Depuis `service-role-manager/` : `./mvnw test` (ou
`mvn -pl service-role-manager test` depuis la racine).

## Particularités

- Sécurité : HTTP Basic + utilisateurs en mémoire (`ali/password123`
  USER, `admin/admin123` ADMIN) — raccourci de démo assumé
  (ARCHITECTURE.md §10). La validation du JWT de `service-sso` est un
  suivi ultérieur.
- Télémétrie (`/api/telemetry`, topic `telemetry.player.action`) :
  brique transverse d'observabilité conservée ici, hors domaine RBAC —
  impureté documentée, extraction en `service-telemetry` = suivi ultérieur
  (spec `docs/superpowers/specs/2026-09-18-sso-role-manager.md` §5).
