package com.consultorprocessos.auth.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends DomainException {
    public EmailNotVerifiedException() {
        super("EMAIL_NOT_VERIFIED",
            "Confirme seu e-mail antes de fazer login.",
            HttpStatus.UNAUTHORIZED);
    }
}