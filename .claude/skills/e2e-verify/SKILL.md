---
name: e2e-verify
description: Procédure manuelle de vérification end-to-end de la stack docker-compose (setup, scénario, tests de charge, logs, arrêt propre). Utiliser pour reproduire à la main ce que fait l'agent e2e-verifier, ou comme sous-étape "Vérifier" d'une tâche de plan.
---

# Vérification end-to-end de la stack

Procédure reprise du style de vérification déjà utilisé pour la Task 12 de
`docs/superpowers/plans/2026-07-18-plan-1-socle-flux-inscription.md`.

## Étapes

1. `make setup` (équivalent `docker compose up -d --build`).
2. `docker compose ps` — attendre que les services concernés soient `Up`.
3. Dérouler le scénario métier pertinent (ex. flux inscription, voir README
   §"Flux inscription (plan 1)").
4. Si la tâche le demande, lancer un test de charge : `make load-test`,
   `make stress-test` ou `make telemetry-test`.
5. En cas de résultat inattendu : `make logs` (ou
   `docker compose logs <service>`) avant de conclure à un échec.
6. `make down` en toute fin, que le scénario ait réussi ou échoué.

## Règles

- Jamais `docker compose down -v` sans demande explicite (perte de volumes
  Kafka/MySQL persistés).
- Rapporter un verdict pass/fail explicite avec les extraits de logs qui le
  justifient, pas juste "ça a l'air de marcher".
