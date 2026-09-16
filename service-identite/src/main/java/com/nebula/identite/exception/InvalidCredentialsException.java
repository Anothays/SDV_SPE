package com.nebula.identite.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("identifiants invalides");
    }
}
