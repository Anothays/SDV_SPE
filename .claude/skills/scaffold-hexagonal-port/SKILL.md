---
name: scaffold-hexagonal-port
description: Génère une paire port (domain) + adapter (infrastructure) pour une capacité d'un service Nebula, en suivant la règle .claude/rules/hexagonal-architecture.md. Utiliser quand une tâche de plan de migration hexagonale demande d'extraire une dépendance concrète (JPA, Kafka) derrière une interface de domaine.
---

# Scaffold d'un port + adapter hexagonal

Cette skill produit le squelette de code pour une migration ports/adapters
sur `service-sso` ou `service-role-manager`, en s'appuyant sur la convention
de packages définie dans `.claude/rules/hexagonal-architecture.md`.

## Exemple de référence : `service-role-manager.RoleAssignmentJpaAdapter`

C'est le pattern déjà en place (voir
`.claude/rules/hexagonal-architecture.md` et `service-role-manager/CLAUDE.md`) :
un adapter Spring Data qui implémente `domain.port.out.RoleAssignmentPort`
et mappe `RoleAssignmentEntity` ↔ `RoleAssignment` sans fuite d'entité JPA.

## Étapes

1. **Identifier la capacité** à extraire (ex. "persistance des attributions") et la
   classe concrète actuelle qui la porte (ex. un `JpaRepository` appelé directement).
2. **Créer le port** dans `<pkg>.domain.port.out` : une interface Java pure,
   sans aucun type Spring/JPA/Kafka/Jackson dans sa signature — uniquement
   des types domaine ou des types Java standard.
   ```java
   package com.nebula.rolemanager.domain.port.out;

   public interface RoleAssignmentPort {
       Optional<RoleAssignment> findByPlayerId(String playerId);
       RoleAssignment save(RoleAssignment assignment);
   }
   ```
3. **Déplacer l'implémentation existante** vers
   `<pkg>.infrastructure.adapter.out`, en l'adaptant pour implémenter le
   port (elle garde ses dépendances Spring/JPA — c'est le rôle d'un
   adapter).
4. **Recâbler l'appelant** (use case en `<pkg>.application`) pour dépendre
   du port, jamais de l'adapter concret — injection par interface.
5. **Mettre à jour `.claude/rules/hexagonal-architecture.md`**, section
   "État actuel de la migration", pour refléter le port désormais existant.
6. **Invoquer `hexagonal-boundary-guard`** sur le diff pour confirmer
   l'absence d'import interdit dans le nouveau port/use case.

## Ce que cette skill ne fait pas

- Ne décide pas seule de lancer une migration : elle s'exécute dans le
  cadre d'une tâche de plan superpowers déjà écrite (voir `CLAUDE.md`
  §Workflow) — pas en dehors d'un plan approuvé.
