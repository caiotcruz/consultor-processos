package com.consultorprocessos.auth.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends DomainException {
    public InvalidTokenException() {
        super("INVALID_TOKEN", "Token inválido ou expirado.", HttpStatus.BAD_REQUEST);
    }
}