# Design — Migration hexagonale service-profil & service-identite

Date : 2026-09-18
Statut : validé (brainstorming court avec l'utilisateur en session — périmètre
combiné, exécution enchaînée)
Sujet : `.claude/rules/hexagonal-architecture.md`,
`documents/ARCHITECTURE.md` §4/§5/§11

## 1. Besoin

Aucun des deux services applicatifs (`service-identite`, `service-profil`)
ne respecte aujourd'hui l'architecture hexagonale décrite dans
`.claude/rules/hexagonal-architecture.md` : les deux suivent
`controller → service → repository/entity`, avec dépendance directe des
classes de service vers Spring (`@Service`/`@Transactional`), JPA
(entités, `EntityManager`/`JpaRepository`) et Jackson (`ObjectMapper`).
La règle, l'agent de revue `hexagonal-boundary-guard` et le skill
`scaffold-hexagonal-port` existent déjà pour encadrer et vérifier cette
migration mécaniquement, mais aucun plan superpowers ne l'a encore
exécutée.

Objectif : faire passer les deux services à `domain` / `application` /
`infrastructure.adapter.{in,out}`, sans changer de comportement
fonctionnel observable (mêmes endpoints REST, mêmes contrats Kafka, même
pattern outbox transactionnel).

## 2. Principes

- **`service-profil` en pilote** : son `ProfilRepository` est déjà une
  classe manuelle (pas un `JpaRepository`), donc le candidat naturel pour
  valider le pattern avant de le répliquer sur `service-identite`.
- **Aucun modèle interne partagé entre couches côté framework** : chaque
  agrégat (`Profil`, `Account`) a un modèle domaine POJO distinct de son
  entité JPA. Le mapping domaine ↔ entité vit dans l'adapter
  `infrastructure.adapter.out`.
- **Ports minimaux, pas de sur-ingénierie** : uniquement des
  `domain.port.out.XxxPort` (pas de `domain.port.in.XxxUseCase` — les
  `CLAUDE.md` locaux des deux services n'anticipent que `domain.port.out`
  + `application`). Les use cases sont des classes simples dans
  `application`.
- **Toute dépendance framework interdite en `domain`/`application` devient
  un port** : ça inclut `PasswordEncoder` (Spring Security) et
  `ObjectMapper` (Jackson), pas seulement JPA/Kafka.
- **`@Transactional` se déplace vers l'adapter d'entrée** qui déclenche le
  use case (le use case orchestrant plusieurs ports ne peut plus porter
  l'annotation Spring).
- **Migration interne pure** : aucun changement de contrat REST, de
  contrat d'événement Kafka, ni de comportement transactionnel observable
  (l'écriture outbox reste dans la même transaction que l'entité
  métier — §11 ARCHITECTURE.md).

## 3. Périmètre

Dans le périmètre :
- `service-profil` : `ProfilRepository`, `ProfilService`,
  `ProfilCreationService`, `entity/Profil`, `outbox/*`, controllers,
  consumers/producer Kafka, config.
- `service-identite` : `AccountRepository`, `AuthService`, `JwtService`,
  `entity/Account`, `outbox/*`, `AuthController`, config.

Hors périmètre :
- `service-messaging`, `service-monitoring`, `service-load-testing`
  (pas de logique métier hexagonalisable).
- Toute nouvelle fonctionnalité ou changement de contrat d'API/événement.
- Une librairie partagée entre les deux services (la duplication du
  pattern port/adapter est volontaire, cohérente avec la duplication déjà
  actée des contrats d'événement — cf. plan-1, Global Constraints).
- `domain.port.in.XxxUseCase` (non anticipé par les CLAUDE.md locaux).

## 4. Critères de succès

- `hexagonal-boundary-guard` ne remonte aucun finding bloquant sur le
  diff cumulé des deux services (aucun import
  `org.springframework.*`/`jakarta.persistence.*`/`org.apache.kafka.*`/
  `com.fasterxml.jackson.*` en `domain`/`application`).
- `mvn -pl service-profil test` et `mvn -pl service-identite test`
  restent verts à chaque tâche.
- Vérification e2e (`e2e-verifier`) sans régression fonctionnelle :
  inscription → `players.registered` → création profil via outbox +
  Debezium, inchangée du point de vue client.
- `.claude/rules/hexagonal-architecture.md` (section "État actuel de la
  migration"), `service-profil/CLAUDE.md`, `service-identite/CLAUDE.md`
  et `documents/ARCHITECTURE.md` reflètent le nouvel état.
