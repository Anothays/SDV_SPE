# Design — Architecture back-end « Nebula »

Date : 2026-07-18
Statut : validé section par section en brainstorming
Sujet : `documents/Projet_d'Architecture_Back-End_(4j).pdf`

## 1. Besoin client (auto-imposé)

Plateforme back-end d'un jeu vidéo compétitif en ligne (« Nebula »). Périmètre retenu :
le **cycle de vie complet d'un match** — inscription d'un joueur, entrée en file de
matchmaking, formation du match, déroulement (simulé), puis recalcul du classement et
crédit des récompenses. Les domaines UGC et Modération sont décrits comme hors périmètre
d'implémentation ; Administration se limite au rôle ADMIN du JWT.

Objectif pédagogique : démontrer une architecture microservices **event-driven** où les
services ne se connaissent pas — aucun appel HTTP inter-services, toute communication
passe par Kafka.

## 2. Principes d'architecture

- **Microservices chorégraphiés** (pas d'orchestrateur) : chaque service réagit aux
  événements et publie les siens. La vision globale du flux vit dans les diagrammes et
  le monitoring, pas dans le code.
- **Event-carried state transfer** : les événements portent toutes les données utiles ;
  un consommateur n'a jamais besoin d'interroger un autre service.
- **Database per service** : techno de persistance choisie par besoin, jamais partagée.
- **REST uniquement en entrée** (client → gateway → service), Kafka entre services.
- **Consommateurs idempotents** : sûreté garantie sans orchestration.
- **Monitoring autonome et non intrusif** (section 18 du sujet) : collecte pull,
  panne du monitoring sans impact sur la plateforme.

## 3. Vue d'ensemble

```
Client jeu / web
      │ HTTPS
      ▼
API Gateway (Spring Cloud Gateway — validation JWT, routage)
      │ REST
      ▼
Identité   Profil   Matchmaking   Classement   Économie      Game-Session
PostgreSQL MySQL    Redis         Redis        PostgreSQL    (sans BDD, sans REST)
      ▲▼ produce / consume
──────────────────── Kafka ────────────────────
players.registered · matches.created · matches.completed

À part : Monitoring (Prometheus / Grafana / Alertmanager) + Load testing (JMeter)
```

### Flux 1 — Inscription

Client → Identité (crée le compte, émet le JWT) → `players.registered` → Profil
(crée le profil joueur en réaction).

### Flux 2 — Cycle de vie d'un match (1 événement, N consommateurs)

Client « Jouer » → Matchmaking (file Redis, appariement MMR) → `matches.created` →
Game-Session (simule la partie) → `matches.completed` → consommé en parallèle par
**Classement** (ELO) et **Économie** (récompenses), dans des consumer groups distincts,
sans se connaître.

## 4. Topics Kafka & contrats d'événements

Convention de nommage : `<domaine>.<événement au passé>`. Un topic = un fait métier,
jamais une commande adressée à un service précis.

| Topic | Producteur | Consommateurs | Clé | Partitions | Rétention |
|---|---|---|---|---|---|
| `players.registered` | Identité | Profil | playerId | 3 | 7 jours |
| `matches.created` | Matchmaking | Game-Session | matchId | 3 | 24 h |
| `matches.completed` | Game-Session | Classement, Économie | matchId | 3 | 7 jours |
| `players.profil.created` | Profil (existant) | — (télémétrie) | playerId | 3 | 7 jours |
| `*.dlt` | consommateurs en échec | supervision | héritée | 1 | 14 jours |

Format : JSON. Enveloppe commune : `eventId` (UUID, idempotence), `eventVersion`,
`occurredAt` (ISO-8601). Évolution citée : Avro + Schema Registry.

### `players.registered` v1

```json
{
  "eventId": "uuid", "eventVersion": 1, "occurredAt": "ISO-8601",
  "playerId": "uuid", "username": "string", "region": "EU|NA|ASIA"
}
```

Pas d'email ni de mot de passe : les PII restent confinées au service Identité.

### `matches.created` v1

```json
{
  "eventId": "uuid", "eventVersion": 1, "occurredAt": "ISO-8601",
  "matchId": "uuid", "gameMode": "ranked-1v1",
  "players": [ { "playerId": "uuid", "mmr": 1240 }, { "playerId": "uuid", "mmr": 1198 } ]
}
```

### `matches.completed` v1

```json
{
  "eventId": "uuid", "eventVersion": 1, "occurredAt": "ISO-8601",
  "matchId": "uuid", "gameMode": "ranked-1v1", "durationSeconds": 312,
  "winnerId": "uuid",
  "players": [ { "playerId": "uuid", "score": 13 }, { "playerId": "uuid", "score": 7 } ]
}
```

Autoportant : Classement et Économie n'appellent personne.

## 5. Services

### API Gateway — Spring Cloud Gateway, sans persistance

Point d'entrée unique : routage par préfixe, validation de la signature JWT, rejet en
amont des requêtes non authentifiées. Routage : `/auth/**` → Identité (public),
`/profils/**` → Profil, `/matchmaking/**` → Matchmaking, `/leaderboard/**` → Classement,
`/wallets/**` → Économie. Évolutions citées : rate limiting, CORS.

### Identité — Spring Boot, PostgreSQL

Comptes et authentification. Seul détenteur des PII (email, hash bcrypt). Émet les JWT
signés RS256 (rôles PLAYER / ADMIN). API : `POST /auth/register`, `POST /auth/login`.
Données : `account(id, username, email, password_hash, role, created_at)`.
Produit `players.registered`.

### Profil — Spring Boot, MySQL (service existant, adapté)

Données publiques du joueur. Le profil est créé **en réaction** à `players.registered`
— la création par POST direct disparaît. API : `GET/PUT /profils/{playerId}`.
Données : `profil(player_id, username, region, level, created_at, updated_at)`.
Consomme `players.registered`, produit `players.profil.created` (conservé, télémétrie).

### Matchmaking — Spring Boot, Redis

File d'attente et appariement. Sorted set trié par MMR ; un scheduler apparie les
joueurs proches, avec tolérance d'écart qui s'élargit avec le temps d'attente. État
éphémère assumé (tickets TTL 5 min). API : `POST/DELETE /matchmaking/queue`,
`GET /matchmaking/status`. Données : `ZSET queue:{gameMode}` (score = MMR),
`HASH ticket:{playerId}`. Produit `matches.created`.

### Game-Session — Node.js/TypeScript, sans persistance, sans API REST

Simulateur tenant lieu de flotte de serveurs de jeu dédiés (fidèle à l'écosystème réel :
le matchmaking n'apparie que, le serveur de jeu autoritatif produit le résultat).
Consomme `matches.created`, simule (délai aléatoire, tirage vainqueur/scores), publie
`matches.completed`. Stateless : rejouable sans risque. Démontre aussi que Kafka
découple les stacks (polyglotte).

### Classement — Spring Boot, Redis

Projection événementielle : à chaque `matches.completed`, recalcule l'ELO des deux
joueurs et met à jour le leaderboard. Cohérence éventuelle assumée. API :
`GET /leaderboard?top=100`, `GET /leaderboard/players/{id}`. Données :
`ZSET leaderboard` (score = ELO), `HASH player:{id}` (elo, wins, losses),
`SET processed-events` (idempotence par eventId).

### Économie — Spring Boot, PostgreSQL

Portefeuille et récompenses. Ledger append-only : le solde est la somme des
transactions. **Idempotence garantie par la base** : `UNIQUE(match_id, player_id)` —
jamais de double crédit même si l'événement est rejoué. API : `GET /wallets/{playerId}`,
`GET /wallets/{playerId}/transactions` (lecture seule — aucun endpoint de crédit).
Données : `wallet(player_id, balance)`,
`ledger(id, player_id, match_id, amount, reason, created_at)`.

## 6. Erreurs & résilience

Principe : aucun événement perdu, aucun événement appliqué deux fois, une panne locale
ne se propage jamais.

- **Consommateurs** : offset commité après traitement réussi (*at-least-once*),
  retry ×3 avec backoff exponentiel, puis dead letter topic `<topic>.dlt` + alerte.
  Doublons neutralisés par l'idempotence (contrainte UNIQUE côté Économie, set
  d'eventId côté Classement).
- **Producteurs** : `acks=all`, producteur idempotent Kafka.

| Panne | Impact | Reprise |
|---|---|---|
| Classement/Économie down 10 min | Aucun ailleurs ; lag Kafka | Reprise à l'offset, rattrapage complet, zéro perte |
| Kafka down | REST toujours servi ; publications en échec | Retries producteur ; limite documentée (compte créé sans événement) → *transactional outbox* cité en évolution |
| Redis matchmaking down | File perdue (éphémère assumé) | Les clients re-soumettent ; matchs déjà formés non affectés |
| Game-Session crash en simulation | Match non conclu | Offset non commité → reconsommation et rejeu (stateless) |
| Monitoring down | **Aucun** (pull non intrusif) | Redémarrage indépendant ; trou de métriques, pas de perte métier |

## 7. Sécurité

- **JWT RS256** : Identité seule détient la clé privée ; gateway et services valident
  avec la clé publique — stateless, aucun secret partagé, aucun appel à Identité.
  Mots de passe bcrypt, expiration courte des tokens.
- **Autorisation** : rôles PLAYER/ADMIN portés par le JWT, contrôlés par endpoint ;
  un joueur n'agit que sur ses ressources (`playerId` issu du token, jamais du body).
- **Règles serveur non contournables** : aucun endpoint ne crédite un portefeuille ni
  ne soumet un résultat de match — ces faits n'existent que par événements internes.
- **Surface d'exposition** : seule la gateway est exposée ; services et Kafka sur le
  réseau Docker interne. PII confinée à Identité, jamais dans les événements.
- Évolutions citées : rate limiting, mTLS inter-services, TLS/SASL Kafka.

## 8. Observabilité (monitoring autonome — section 18)

- **Collecte pull non intrusive** : Micrometer `/actuator/prometheus` (Spring),
  `prom-client` `/metrics` (Node). Le monitoring peut surveiller n'importe quelle
  application ; sa panne n'affecte jamais la plateforme.
- **Métriques** : métier (tickets en file, temps d'attente, matchs formés/conclus par
  minute, récompenses créditées), Kafka (consumer lag par groupe, messages en DLT),
  technique (latence p95, taux 5xx, JVM — dashboards existants réutilisés).
- **Alertes** (Alertmanager) : service down, taux 5xx, latence p95, consumer lag
  croissant, DLT non vide.
- **Traçabilité** : logs JSON corrélés par `eventId`/`matchId`. Évolution citée :
  OpenTelemetry.

## 9. Tests

Pyramide : unitaires (calcul ELO, appariement MMR, montants de récompense) →
**tests de contrat** (JSON Schema par topic, validé côté producteur et consommateur —
le contrat inter-services) → intégration (Testcontainers Kafka + BDD par service) →
E2E (docker-compose : inscription → match → ELO + récompense vérifiés).
Non-régression : contrats + E2E cassent un test avant de casser un service.

## 10. Hypothèses de charge (section 16)

| Hypothèse | Valeur | Conséquence |
|---|---|---|
| Joueurs connectés en pic | 5 000 | Dimensionne gateway et lectures leaderboard |
| Tickets matchmaking | ~250/min | Scheduler sur ZSET : O(log n), très à l'aise |
| Matchs conclus | ~100/min (≈ 2 evt/s < 1 Ko) | Kafka surdimensionné à dessein — la marge est le discours |
| Lectures leaderboard | ~50 req/s | Sorted set Redis, pas de cache supplémentaire |

Montée en charge : scaling horizontal par service ; partitions Kafka = parallélisme des
consommateurs. Tests JMeter (existants) via la gateway : scénario nominal + stress,
résultats InfluxDB/Grafana.

## 11. Décisions actées (avec alternatives écartées)

| Décision | Alternative écartée | Raison |
|---|---|---|
| Chorégraphie pure | Orchestration / saga hybride | Fidèle au principe « les services ne se connaissent pas » ; idempotence remplace l'orchestrateur |
| Event-carried state transfer | Événements-notifications minces | Zéro appel HTTP inter-services |
| Polyglotte ciblé (Node pour Game-Session) | Tout Spring Boot / polyglotte complet | Démontre le découplage des stacks au moindre coût |
| Persistance par besoin | SQL partout | Justification riche : Redis (file, leaderboard), PostgreSQL (ACID), MySQL (existant) |
| Service Identité complet + gateway codée | Sécurité sur le papier | Choix utilisateur : chaîne démontrée de bout en bout |
| JWT RS256 | HS256 secret partagé | Aucun secret distribué |

## 12. Hors périmètre

UGC, Modération, admin avancée (décrits au dossier uniquement) ; transactional outbox,
Avro/Schema Registry, tracing distribué, rate limiting, mTLS (cités en évolution) ;
boutique/achats (l'Économie se limite au portefeuille et aux récompenses).
