package com.consultorprocessos.auth.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AccountSuspendedException extends DomainException {
    public AccountSuspendedException() {
        super("ACCOUNT_SUSPENDED", "Conta suspensa. Entre em contato com o suporte.",
            HttpStatus.UNAUTHORIZED);
    }
}