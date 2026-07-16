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

L'application se connecte à une base MySQL 8 dont les credentials sont définis dans `src/main/resources/application.properties` :

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

## Messaging avec Kafka

L'application publie un événement métier à chaque création de profil, selon une architecture orientée événements :

```
POST /api/profils
      │
      ▼
ProfilService.saveProfil()
      ├── 1. Sauvegarde MySQL (synchrone)
      └── 2. Publication de l'événement sur le topic `profil-created` (asynchrone)
                    │
                    ▼
            ProfilEventConsumer (@KafkaListener, groupe `mademo`)
```

### Composants

| Classe | Rôle |
|--------|------|
| `kafka/KafkaTopicConfig` | Déclare le topic `profil-created` (1 partition, 1 réplique) |
| `kafka/ProfilEventProducer` | Publie l'événement via `KafkaTemplate` (JSON) |
| `kafka/ProfilEventConsumer` | Consomme l'événement et le journalise |

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

#### `./config/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml`
Injecte la configuration de scraping dans Prometheus. Permet de modifier les cibles sans reconstruire l'image.

#### `prometheus_data:/prometheus`
Volume Docker nommé qui persiste la base de données time-series de Prometheus entre les redémarrages.

#### `./config/grafana/provisioning:/etc/grafana/provisioning`
Permet à Grafana de **charger automatiquement** les datasources et les dashboards au démarrage, sans action manuelle dans l'UI.

Ce dossier contient deux sous-dossiers :
- `datasources/` — fichiers YAML déclarant les sources de données (Prometheus, InfluxDB)
- `dashboards/` — fichier YAML indiquant à Grafana où trouver les fichiers JSON des dashboards

#### `./config/grafana/dashboards:/var/lib/grafana/dashboards`
Contient les fichiers JSON des dashboards Grafana. Tout fichier `.json` déposé ici est automatiquement importé au démarrage via le provisioning. Cela permet de **versionner les dashboards dans Git** et de les déployer sans intervention manuelle.

Dashboards disponibles :
- `jvm-micrometer.json` — métriques JVM de l'application Spring Boot
- `apache-jmeter-influxdb.json` — résultats des tests JMeter en temps réel
- `jmeter-dashboard.json` — vue synthétique des tests de charge
- `kafka-client-metrics.json` — métriques client Kafka (producteur/consommateur) de l'application

#### Volumes JMeter
- `./config/jmeter/run-test.sh:/scripts/run-test.sh` — script d'exécution des tests (versionné)
- `./config/jmeter/test-plans:/test-plans` — plans de test `.jmx`
- `./config/jmeter/results:/results` — fichiers de résultats `.jtl`
- `./config/jmeter/reports:/reports` — rapports HTML générés après chaque test

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

### Lancer les tests

La logique d'exécution est dans `jmeter/run-test.sh` (monté dans le container, versionné). Le plan est choisi via la variable `TEST_PLAN` (défaut : `profil-api-load-test.jmx`) ; résultats et rapports sont nommés d'après le plan, donc load et stress ne s'écrasent pas.

```bash
# Via le Makefile (recommandé)
make load-test
make stress-test

# Équivalent docker compose
docker compose --profile testing run --rm jmeter
TEST_PLAN=profil-api-stress-test.jmx docker compose --profile testing run --rm jmeter
```

Autres cibles : `make up`, `make down`, `make logs`, `make help`.

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
  Événement `profil-created` publié à chaque création de profil, consommé par un `@KafkaListener`. Métriques client exposées via Micrometer/Prometheus et visualisées dans le dashboard Grafana dédié.
  Choix de Kafka plutôt que RabbitMQ : adapté aux flux à fort volume et à l'event sourcing, cohérent avec la vision plateforme de jeu (télémétrie joueurs, événements de matchs).

### Autres

- [ ] Configurer les règles d'alerting dans Alertmanager (actuellement démarré mais sans configuration)
- [ ] Ajouter un healthcheck sur le service `app` dans `docker-compose.yml` pour que Prometheus ne démarre qu'une fois Spring Boot prêt
- [ ] Persister les données InfluxDB avec un volume Docker
