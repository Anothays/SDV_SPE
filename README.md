# MaDemo — Monitoring & Observabilité

API REST Spring Boot avec une stack de monitoring complète : Prometheus, Grafana, JMeter et InfluxDB, le tout orchestré via Docker Compose.

---

## Stack technique

| Composant | Rôle |
|-----------|------|
| Spring Boot 3.4.1 + Java 17 | Application REST |
| MySQL 8 | Base de données relationnelle |
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

## Docker Compose

Tous les services sont définis dans `docker-compose.yml` et communiquent sur le réseau interne `monitoring-net`.

### Volumes montés

Les volumes permettent de persister des données ou d'injecter de la configuration dans les containers.

#### `./data:/var/lib/mysql`
Persiste les données MySQL sur le disque local. Sans ce volume, toutes les données seraient perdues à chaque `docker compose down`.

#### `./prometheus.yml:/etc/prometheus/prometheus.yml`
Injecte la configuration de scraping dans Prometheus. Permet de modifier les cibles sans reconstruire l'image.

#### `prometheus_data:/prometheus`
Volume Docker nommé qui persiste la base de données time-series de Prometheus entre les redémarrages.

#### `./grafana/provisioning:/etc/grafana/provisioning`
Permet à Grafana de **charger automatiquement** les datasources et les dashboards au démarrage, sans action manuelle dans l'UI.

Ce dossier contient deux sous-dossiers :
- `datasources/` — fichiers YAML déclarant les sources de données (Prometheus, InfluxDB)
- `dashboards/` — fichier YAML indiquant à Grafana où trouver les fichiers JSON des dashboards

#### `./grafana/dashboards:/var/lib/grafana/dashboards`
Contient les fichiers JSON des dashboards Grafana. Tout fichier `.json` déposé ici est automatiquement importé au démarrage via le provisioning. Cela permet de **versionner les dashboards dans Git** et de les déployer sans intervention manuelle.

Dashboards disponibles :
- `jvm-micrometer.json` — métriques JVM de l'application Spring Boot
- `apache-jmeter-influxdb.json` — résultats des tests JMeter en temps réel
- `jmeter-dashboard.json` — vue synthétique des tests de charge

#### Volumes JMeter
- `./jmeter/test-plans:/test-plans` — plans de test `.jmx`
- `./jmeter/results:/results` — fichiers de résultats `.jtl`
- `./jmeter/reports:/reports` — rapports HTML générés après chaque test

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

```bash
# Test de charge uniquement
docker compose --profile testing up jmeter

# Tous les services + test
docker compose --profile testing up --build
```

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

- [ ] **Intégrer le test de stress dans le service JMeter** du `docker-compose.yml`
  Le service JMeter actuel n'exécute que `profil-api-load-test.jmx`. Il serait utile d'ajouter une variable d'environnement ou un second service pour lancer `profil-api-stress-test.jmx` à la demande.

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

- [ ] **Intégrer RabbitMQ ou Kafka**
  Pour une architecture orientée événements, il serait pertinent d'ajouter un broker de messages :
  - **RabbitMQ** : adapté pour des queues de tâches simples, routing flexible
  - **Kafka** : adapté pour des flux de données à fort volume, event sourcing

  Les deux s'intègrent nativement avec Spring Boot (`spring-rabbit` / `spring-kafka`) et exposent leurs métriques via Micrometer/Prometheus.

### Autres

- [ ] Configurer les règles d'alerting dans Alertmanager (actuellement démarré mais sans configuration)
- [ ] Ajouter un healthcheck sur le service `app` dans `docker-compose.yml` pour que Prometheus ne démarre qu'une fois Spring Boot prêt
- [ ] Persister les données InfluxDB avec un volume Docker
