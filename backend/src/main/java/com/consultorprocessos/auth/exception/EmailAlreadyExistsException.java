package com.consultorprocessos.auth.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends DomainException {
    public EmailAlreadyExistsException() {
        super("EMAIL_ALREADY_EXISTS", "Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
    }
}