package com.nebula.identite.auth;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException() {
        super("username ou email déjà utilisé");
    }
}
