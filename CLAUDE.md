# SDV_SPE — Nebula

Plateforme de démo microservices événementiels (Spring Boot + Kafka +
Outbox/Debezium) servant de preuve de concept à `documents/ARCHITECTURE.md`.
Ce fichier gouverne tout travail d'agent sur ce repo.

## Workflow (Superpowers d'abord)

Tout travail non trivial (feature, migration, changement d'architecture)
passe par le workflow **superpowers** : brainstorming →
`docs/superpowers/specs/YYYY-MM-DD-slug.md` → writing-plans →
`docs/superpowers/plans/YYYY-MM-DD-plan-N-slug.md` →
`subagent-driven-development` / `executing-plans`. Ne pas sauter directement
au code sauf correctif trivial d'un seul fichier.

Les agents (`.claude/agents/`) et skills (`.claude/skills/`) ci-dessous sont
des spécialistes invoqués **comme étapes d'une tâche de plan** (ex. une
sous-étape "Vérifier"), jamais un raccourci pour éviter d'écrire une spec/un
plan.

## Repo Map

- `service-sso/` — authentification, JWT RS256, package `com.nebula.sso`
- `service-role-manager/` — RBAC (rôles joueurs), package `com.nebula.rolemanager`
- `service-messaging/`, `service-monitoring/`, `service-load-testing/`
- `documents/ARCHITECTURE.md` — dossier d'architecture (18 sections)
- `docs/superpowers/{specs,plans}/` — specs et plans datés

## Commandes

- `make setup` / `make down` / `make logs`
- `make load-test` / `make stress-test` / `make telemetry-test`
- `mvn -pl service-sso test`, `mvn -pl service-role-manager test` (ou `./mvnw test` depuis le service)

## Conventions

- Commits conventionnels, en anglais, un commit par tâche de plan
- Commentaires en français tolérés dans le code existant (ne pas reformater
  en masse)
- DTO aux frontières, jamais de modèle interne partagé entre domaines
- Aucune PII dans les événements Kafka
- JWT RS256, clés dev préfixées `dev-`

## Guard-rails

@.claude/rules/hexagonal-architecture.md

Rappel ARCHITECTURE.md : §4 un domaine = un schéma, aucune lecture croisée
de table ; §5 aucun modèle interne partagé.

## Docs Sync

Tout changement touchant une frontière documentée (schéma, contrat
d'événement, découpage de package) met à jour `documents/ARCHITECTURE.md`
et/ou `README.md` dans un commit `docs:` séparé — déléguer à l'agent
`docs-sync`.

## MCP

Aucun MCP configuré actuellement. `gh` CLI + outils natifs
(Read/Write/Edit/Glob/Grep/Bash) couvrent les besoins existants ; un MCP
GitHub ou filesystem dupliquerait une capacité déjà couverte. Un MCP DB
serait prématuré : la règle "pas de lecture croisée de table" (§4) est déjà
vérifiable statiquement. À réévaluer si un futur plan introduit un vrai
risque de dérive de schéma (ex. mapping Debezium vs schéma réel) — dans ce
cas, MCP Postgres lecture seule uniquement, credentials en variable
d'environnement, jamais committés.

## Overrides locaux

`CLAUDE.local.md` (gitignored) : notes personnelles/machine uniquement,
jamais de règle de projet.
