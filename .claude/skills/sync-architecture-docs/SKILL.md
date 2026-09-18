---
name: sync-architecture-docs
description: Détermine quelle section de documents/ARCHITECTURE.md ou README.md correspond à un changement de code donné (schéma, contrat d'événement, découpage de package) et propose l'édition minimale correspondante. Utilisée par l'agent docs-sync, invocable aussi directement.
---

# Synchronisation ARCHITECTURE.md / README.md

## Classification diff → section

| Type de changement | Section(s) ARCHITECTURE.md |
|---|---|
| Schéma de base de données, moteur de persistance | §4, §7 |
| Frontière/DTO, contrat d'API REST, contrat d'événement Kafka | §5, §6, §9 |
| Pattern Outbox / Debezium CDC, dual-write | §11 |
| Découpage de package `domain`/`application`/`infrastructure` | §5 + `.claude/rules/hexagonal-architecture.md` |
| Observabilité, alerting | §13, §15 |

## Méthode

1. Lire le diff (fourni par l'appelant ou via `git diff`).
2. Repérer le(s) type(s) de changement via la table ci-dessus (un diff peut
   toucher plusieurs lignes).
3. Localiser la section exacte dans `documents/ARCHITECTURE.md` (numéro +
   titre) et le passage précis à modifier — ne pas réécrire toute la
   section.
4. Rédiger une édition minimale, dans le style existant (français, tableau
   si le contenu environnant est un tableau, phrase courte sinon).
5. Si le README référence aussi ce point (ex. section "Flux inscription"),
   proposer l'édition correspondante là aussi.

## Sortie attendue

Le texte exact à insérer/modifier, avec la localisation précise (numéro de
section, ligne ou table visée) — pas une description vague de ce qu'il
faudrait faire.
