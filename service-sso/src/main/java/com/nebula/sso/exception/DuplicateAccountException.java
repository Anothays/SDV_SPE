package com.nebula.sso.exception;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException() {
        super("username ou email déjà utilisé");
    }
}
