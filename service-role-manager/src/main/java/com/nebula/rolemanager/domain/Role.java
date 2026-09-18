package com.nebula.rolemanager.domain;

/**
 * Les trois rôles RBAC du dossier d'architecture (§10). Un joueur porte un
 * seul rôle à la fois, aligné sur le claim scalaire "role" du JWT émis par
 * service-sso.
 */
public enum Role {
    PLAYER,
    MODERATOR,
    ADMIN
}
