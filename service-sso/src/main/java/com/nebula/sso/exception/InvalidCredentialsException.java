package com.nebula.sso.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("identifiants invalides");
    }
}
