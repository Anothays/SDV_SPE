# Architecture hexagonale (ports & adapters)

## Pourquoi

Cette règle renforce des frontières déjà écrites dans `documents/ARCHITECTURE.md` :
§4 (un domaine = un schéma, aucune lecture croisée de table), §5 (DTO aux
frontières, aucun modèle interne partagé) et §11 (pattern Outbox). Elle ne
propose pas une architecture concurrente — elle rend ces frontières
vérifiables mécaniquement dans le code.

## Règle de couche

Le code sous `<pkg>.domain` et `<pkg>.application` ne doit **jamais**
importer :
- `org.springframework.*`
- `jakarta.persistence.*` / `javax.persistence.*`
- `org.apache.kafka.*`
- `com.fasterxml.jackson.*`

Les **ports** sont des interfaces possédées par le domaine (ex.
`domain.port.out.RoleAssignmentPort`). Les **adapters** sont les
implémentations côté framework de ces ports, situées en `infrastructure`.

## Convention de packages

| Package | Contenu |
|---|---|
| `<pkg>.domain` | Entités, value objects, services domaine, ports (`domain.port.out.XxxPort`, `domain.port.in.XxxUseCase`) |
| `<pkg>.application` | Use cases orchestrant les ports, DTO de frontière |
| `<pkg>.infrastructure.adapter.in` | Controllers REST, consumers Kafka |
| `<pkg>.infrastructure.adapter.out` | Repositories JPA, producers Kafka, écriture outbox — implémentent les ports `domain.port.out` |

## État actuel de la migration

Les deux services sont hexagonaux (plan
`docs/superpowers/plans/2026-09-18-plan-2-hexagonal-migration.md`) et ont
été renommés/refondus par le plan
`docs/superpowers/plans/2026-09-18-plan-3-sso-role-manager.md`.

`service-role-manager` (ex-`service-profil`, RBAC) :
`com.nebula.rolemanager.domain` (`Role`, `RoleAssignment`,
`domain.port.out.RoleAssignmentPort`, `EventPublisherPort`),
`com.nebula.rolemanager.application` (`AssignDefaultRoleUseCase`,
`ChangeRoleUseCase`, `RoleAssignmentQueryService`),
`com.nebula.rolemanager.infrastructure.adapter.out.persistence.RoleAssignmentJpaAdapter`,
`com.nebula.rolemanager.infrastructure.adapter.out.outbox.OutboxEventPublisherAdapter`,
`com.nebula.rolemanager.infrastructure.adapter.in.web` /
`...adapter.in.kafka`.

`service-sso` (ex-`service-identite`, authentification) :
`com.nebula.sso.domain` (`Account`, `domain.port.out.AccountPort`,
`TokenPort`, `PasswordHasherPort`, `EventPublisherPort`),
`com.nebula.sso.application` (`RegisterUseCase`, `LoginUseCase`,
`ApplyRoleAssignmentUseCase`),
`com.nebula.sso.infrastructure.adapter.out.persistence.AccountJpaAdapter`,
`com.nebula.sso.infrastructure.adapter.out.security` (`JwtTokenAdapter`,
`BCryptPasswordHasherAdapter`),
`com.nebula.sso.infrastructure.adapter.out.outbox.OutboxEventPublisherAdapter`,
`com.nebula.sso.infrastructure.adapter.in.web.AuthController`,
`com.nebula.sso.infrastructure.adapter.in.kafka.RoleAssignedConsumer`.

## Attente de revue

Un import interdit détecté dans `domain` ou `application` est un finding
**bloquant** pour l'agent `hexagonal-boundary-guard` — pas une remarque de
style à laisser passer.
