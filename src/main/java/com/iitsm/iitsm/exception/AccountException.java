package com.iitsm.iitsm.exception;

/** Invalid sign-up or account change (weak password, username taken, not allowed, ...). */
public class AccountException extends RuntimeException {
    public AccountException(String message) {
        super(message);
    }
}
