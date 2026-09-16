# MaDemo — Monitoring & Observabilité

API REST Spring Boot avec une stack de monitoring complète : Prometheus, Grafana, JMeter et InfluxDB, le tout orchestré via Docker Compose.

---

## Stack technique

| Composant | Rôle |
|-----------|------|
| Spring Boot 3.4.1 + Java 17 | Application REST |
| MySQL 8 | Base de données relationnelle |
| Kafka (KRaft) | Broker de messages — événements métier |
| Prometheus | Collecte des métriques (scraping) |
| Grafana | Visualisation des métriques |
| InfluxDB | Stockage des résultats JMeter |
| JMeter | Tests de charge et de stress |
| Alertmanager | Gestion des alertes Prometheus |

---

## Base de données MySQL

L'application se connecte à une base MySQL 8 dont les credentials sont définis dans `profil-service/src/main/resources/application.properties` :

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/maBase
spring.datasource.username=devuser
spring.datasource.password=devpassword
```

En environnement Docker, ces valeurs sont surchargées par les variables d'environnement du service `app` dans `docker-compose.yml`.

Le pool de connexions HikariCP est configuré avec 10 connexions max et 2 connexions minimum en veille.

---

## Monitoring avec Prometheus + Grafana

### Intégration Micrometer dans l'application

La dépendance suivante dans `pom.xml` permet à Spring Boot d'exposer ses métriques internes au format Prometheus :

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Combinée à `spring-boot-starter-actuator`, elle expose l'endpoint `/actuator/prometheus` qui retourne toutes les métriques JVM, HTTP, mémoire, threads, GC, etc.

### Prometheus

Prometheus scrape l'endpoint toutes les 15 secondes (`prometheus.yml`) :

```yaml
scrape_configs:
  - job_name: 'spring-boot-app'
    static_configs:
      - targets: ['app:8080']
    metrics_path: '/actuator/prometheus'
```

### Grafana

Grafana se connecte à Prometheus pour visualiser les métriques en temps réel. Le dashboard **JVM (Micrometer)** présente :
- Uptime, heure de démarrage
- Utilisation heap / non-heap
- Taux de requêtes HTTP, erreurs, durée
- Threads, GC, Classloading, Buffer pools

---

## Alerting avec Prometheus + Alertmanager

**Division du travail** : Prometheus détecte (évalue les règles PromQL), Alertmanager notifie (déduplique, groupe, route vers Discord).

### Règles d'alerte (`monitoring-service/prometheus/alert-rules.yml`)

| Alerte | Condition | Durée | Sévérité |
|--------|-----------|-------|----------|
| `AppDown` | cible de scrape injoignable | 1 min | critical |
| `HighErrorRate` | > 1 % de réponses 5xx | 2 min | critical |
| `HighLatencyP99` | p99 des requêtes HTTP > 1 s | 2 min | warning |
| `KafkaConsumerLagHigh` | > 100 messages de lag | 5 min | warning |

Le calcul du p99 nécessite les buckets d'histogramme, activés dans `application.properties` (`management.metrics.distribution.percentiles-histogram.http.server.requests=true`).

### Notifications Discord

Alertmanager route toutes les alertes vers un webhook Discord (`monitoring-service/alertmanager/alertmanager.yml`). L'URL du webhook est lue depuis `monitoring-service/alertmanager/discord_webhook_url` — fichier **gitignoré** (secret), monté dans le container. La résolution d'une alerte est aussi notifiée (`send_resolved`).

### Tester la chaîne

```bash
docker stop app      # après ~1 min : alerte AppDown sur Discord
docker start app     # notification "resolved"
```

UI de contrôle : http://localhost:9090/alerts (état des règles) · http://localhost:9093 (Alertmanager).

---

## Messaging avec Kafka

Deux familles d'événements implémentées, aux caractéristiques volontairement opposées :

| Topic | Famille | Partitions | Rétention | Clé |
|-------|---------|-----------|-----------|-----|
| `players.profil.created` | **Fait métier** — chaque message compte | 1 | défaut (7 j) | id du profil |
| `telemetry.player.action` | **Télémétrie** — fort volume, valeur individuelle faible | 6 | 24 h | playerId |

(3ᵉ famille — **état courant**, topic compacté — décrite dans `documents/ARCHITECTURE.md` §9, non implémentée.)

### Flux 1 — fait métier

```
POST /api/profils
      │
      ▼
ProfilService.saveProfil()
      ├── 1. Sauvegarde MySQL (synchrone)
      └── 2. Publication de l'événement sur le topic `players.profil.created` (asynchrone)
                    │
                    ▼
            ProfilEventConsumer (@KafkaListener, groupe `mademo`)
```

### Flux 2 — télémétrie

```
POST /api/telemetry  ──► 202 Accepted immédiat (fire-and-forget, aucune écriture MySQL)
      │
      ▼
topic telemetry.player.action (6 partitions)
      │
      ▼
TelemetryEventConsumer (groupe `mademo-telemetry`, concurrency 3)
      └── agrège en compteurs Micrometer `telemetry_player_actions_total{action=...}`
          → visibles dans Prometheus/Grafana
```

### Composants

| Classe | Rôle |
|--------|------|
| `kafka/KafkaTopicConfig` | Déclare les topics (partitions, rétention) |
| `kafka/ProfilEventProducer` / `ProfilEventConsumer` | Fait métier : publication + journalisation |
| `kafka/TelemetryEventProducer` / `TelemetryEventConsumer` | Télémétrie : publication fire-and-forget + agrégation Micrometer |
| `controller/TelemetryController` | Endpoint d'ingestion `POST /api/telemetry` (202) |

**Résilience** : l'envoi Kafka est asynchrone — une panne du broker ne fait pas échouer la requête HTTP, l'erreur est seulement journalisée.

### Broker

Kafka tourne en mode **KRaft** (sans Zookeeper) dans Docker Compose, avec deux listeners :
- `kafka:9092` — accès interne pour les containers du réseau `monitoring-net`
- `localhost:9094` — accès depuis la machine hôte (développement local)

En Docker, l'app utilise `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092` ; en local, `spring.kafka.bootstrap-servers=localhost:9094` (application.properties).

### Observabilité

`spring-kafka` expose automatiquement les métriques **client** (producteur/consommateur de l'application) via Micrometer sur `/actuator/prometheus`. Le dashboard Grafana **Kafka Client (Micrometer)** (`kafka-client-metrics.json`) visualise :
- débit et erreurs de publication, latence d'envoi vers le broker
- débit de consommation, consumer lag, durée de traitement du `@KafkaListener`

> Les métriques du **broker** lui-même (JMX) ne sont pas collectées — nécessiterait un `jmx-exporter` ou `kafka-exporter` (évolution possible).

---

## Docker Compose

Tous les services sont définis dans `docker-compose.yml` et communiquent sur le réseau interne `monitoring-net`.

### Volumes montés

Les volumes permettent de persister des données ou d'injecter de la configuration dans les containers.

> **Note** : les données MySQL ne sont volontairement **pas persistées** (pas de volume sur `/var/lib/mysql`) — chaque `docker compose down` repart d'une base vide, ce qui garantit des tests reproductibles.

#### `./monitoring-service/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml`
Injecte la configuration de scraping dans Prometheus. Permet de modifier les cibles sans reconstruire l'image.

#### `prometheus_data:/prometheus`
Volume Docker nommé qui persiste la base de données time-series de Prometheus entre les redémarrages.

#### `./monitoring-service/grafana/provisioning:/etc/grafana/provisioning`
Permet à Grafana de **charger automatiquement** les datasources et les dashboards au démarrage, sans action manuelle dans l'UI.

Ce dossier contient deux sous-dossiers :
- `datasources/` — fichiers YAML déclarant les sources de données (Prometheus, InfluxDB)
- `dashboards/` — fichier YAML indiquant à Grafana où trouver les fichiers JSON des dashboards

#### `./monitoring-service/grafana/dashboards:/var/lib/grafana/dashboards`
Contient les fichiers JSON des dashboards Grafana. Tout fichier `.json` déposé ici est automatiquement importé au démarrage via le provisioning. Cela permet de **versionner les dashboards dans Git** et de les déployer sans intervention manuelle.

Dashboards disponibles :
- `jvm-micrometer.json` — métriques JVM de l'application Spring Boot
- `apache-jmeter-influxdb.json` — résultats des tests JMeter en temps réel
- `jmeter-dashboard.json` — vue synthétique des tests de charge
- `kafka-client-metrics.json` — métriques client Kafka (producteur/consommateur) de l'application

#### Volumes JMeter
- `./load-testing/jmeter/run-test.sh:/scripts/run-test.sh` — script d'exécution des tests (versionné)
- `./load-testing/jmeter/test-plans:/test-plans` — plans de test `.jmx`
- `./load-testing/jmeter/results:/results` — fichiers de résultats `.jtl`
- `./load-testing/jmeter/reports:/reports` — rapports HTML générés après chaque test

---

## Tests JMeter

JMeter est intégré comme service Docker (profil `testing`) et envoie ses métriques en temps réel vers InfluxDB, visualisables dans Grafana.

### Test de charge — `profil-api-load-test.jmx`

Simule une **charge normale et soutenue** sur l'API.

| Paramètre | Valeur |
|-----------|--------|
| Utilisateurs simultanés | 50 |
| Montée en charge | 15 secondes |
| Durée totale | 60 secondes |
| Endpoint testé | `POST /api/profils` |

**Objectif** : vérifier que l'application tient une charge réaliste sans dégradation des temps de réponse.

### Test de stress — `profil-api-stress-test.jmx`

Simule une **montée progressive en charge** pour identifier le point de rupture de l'application.

| Phase | Utilisateurs | Durée |
|-------|-------------|-------|
| Phase 1 — Charge initiale | 10 | 20s |
| Phase 2 — Charge moyenne | 50 | 20s |
| Phase 3 — Charge maximale | 200 | 20s |

**Objectif** : trouver à partir de quel seuil les temps de réponse se dégradent ou des erreurs apparaissent.

### Test de télémétrie — `telemetry-stress-test.jmx`

Bombarde l'endpoint d'ingestion `POST /api/telemetry` (fire-and-forget vers Kafka).

| Paramètre | Valeur |
|-----------|--------|
| Utilisateurs simultanés | 100 |
| Montée en charge | 10 secondes |
| Durée totale | 60 secondes |
| Assertion | HTTP 202 |

**Objectif** : démontrer la chaîne complète d'ingestion — l'API encaisse (202 rapides), le topic absorbe, le consumer agrège, le débit apparaît dans Grafana, et le consumer lag est surveillé par l'alerte `KafkaConsumerLagHigh`.

### Lancer les tests

La logique d'exécution est dans `load-testing/jmeter/run-test.sh` (monté dans le container, versionné). Le plan est choisi via la variable `TEST_PLAN` (défaut : `profil-api-load-test.jmx`) ; résultats et rapports sont nommés d'après le plan, donc load et stress ne s'écrasent pas.

```bash
# Via le Makefile (recommandé)
make load-test
make stress-test
make telemetry-test

# Équivalent docker compose
docker compose --profile testing run --rm jmeter
TEST_PLAN=profil-api-stress-test.jmx docker compose --profile testing run --rm jmeter
TEST_PLAN=telemetry-stress-test.jmx docker compose --profile testing run --rm jmeter
```

Autres cibles : `make setup`, `make down`, `make logs`, `make help`.

---

## Workflow global

```
┌─────────────────────────────────────────────────────────────────┐
│                        Docker Network                           │
│                                                                 │
│  ┌──────────┐   JDBC    ┌──────────┐                           │
│  │  app     │ ────────► │  MySQL   │                           │
│  │ :8080    │           │  :3306   │                           │
│  └────┬─────┘           └──────────┘                           │
│       │                                                         │
│       │ /actuator/prometheus (scrape 15s)                       │
│       ▼                                                         │
│  ┌──────────┐           ┌──────────┐                           │
│  │Prometheus│ ────────► │ Grafana  │                           │
│  │  :9090   │  datasrc  │  :3000   │                           │
│  └──────────┘           └────┬─────┘                           │
│                              │                                  │
│  ┌──────────┐   HTTP    ┌────┴─────┐                           │
│  │  JMeter  │ ────────► │ InfluxDB │                           │
│  │ (testing)│  push     │  :8086   │                           │
│  └──────────┘           └──────────┘                           │
│                                                                 │
│  ┌─────────────┐                                               │
│  │Alertmanager │ ◄── Prometheus alerts                         │
│  │   :9093     │                                               │
│  └─────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## TODO

### Améliorations prioritaires

- [x] **Intégrer le test de stress dans le service JMeter** du `docker-compose.yml`
  Le plan de test est piloté par la variable d'environnement `TEST_PLAN` (défaut : `profil-api-load-test.jmx`), permettant de lancer `profil-api-stress-test.jmx` à la demande sans dupliquer le service.

### Métriques fonctionnelles

- [ ] **Implémenter des métriques métier** avec Micrometer
  Au-delà des métriques techniques JVM, il est possible d'instrumenter le code métier :
  ```java
  // Exemple : compter le nombre de profils créés
  Counter.builder("profils.created.total")
      .description("Nombre de profils créés")
      .register(meterRegistry)
      .increment();
  ```
  Ces métriques apparaîtront automatiquement dans Prometheus et pourront être visualisées dans Grafana.

### Messaging

- [x] **Intégrer Kafka** (voir section [Messaging avec Kafka](#messaging-avec-kafka))
  Événement `players.profil.created` publié à chaque création de profil, consommé par un `@KafkaListener`. Métriques client exposées via Micrometer/Prometheus et visualisées dans le dashboard Grafana dédié.
  Choix de Kafka plutôt que RabbitMQ : adapté aux flux à fort volume et à l'event sourcing, cohérent avec la vision plateforme de jeu (télémétrie joueurs, événements de matchs).

### Autres

- [x] Configurer les règles d'alerting dans Alertmanager (voir section [Alerting](#alerting-avec-prometheus--alertmanager))
- [ ] Ajouter un healthcheck sur le service `app` dans `docker-compose.yml` pour que Prometheus ne démarre qu'une fois Spring Boot prêt
- [ ] Persister les données InfluxDB avec un volume Docker

---

## Flux inscription (plan 1)

Inscription → `players.registered` → création du profil en réaction (chorégraphie,
aucun appel HTTP inter-services). Détails : `docs/superpowers/specs/2026-07-18-nebula-architecture-design.md`.

- Service Identité : http://localhost:8082 (`POST /auth/register`, `POST /auth/login`)
- Service Profil : http://localhost:8080 (`GET/PUT /api/profils/{playerId}`)
- Démo rapide : voir `docs/superpowers/plans/2026-07-18-plan-1-socle-flux-inscription.md`, Task 12.
