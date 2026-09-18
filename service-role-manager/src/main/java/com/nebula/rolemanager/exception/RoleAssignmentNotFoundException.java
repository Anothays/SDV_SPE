package com.nebula.rolemanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RoleAssignmentNotFoundException extends RuntimeException {

    public RoleAssignmentNotFoundException(String playerId) {
        super("attribution de rôle introuvable pour playerId=" + playerId);
    }
}
