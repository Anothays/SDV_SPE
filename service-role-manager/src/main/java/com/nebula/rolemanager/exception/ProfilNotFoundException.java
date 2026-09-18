package com.nebula.rolemanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProfilNotFoundException extends RuntimeException {

    public ProfilNotFoundException(String playerId) {
        super("profil introuvable pour playerId=" + playerId);
    }
}
