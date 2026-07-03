package com.consultorprocessos.auth.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public class AccountLockedException extends DomainException {
    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("ACCOUNT_LOCKED",
            "Conta temporariamente bloqueada por excesso de tentativas.",
            HttpStatus.UNAUTHORIZED);
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() { return lockedUntil; }
}