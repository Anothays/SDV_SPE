package com.nebula.identite.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("identifiants invalides");
    }
}
