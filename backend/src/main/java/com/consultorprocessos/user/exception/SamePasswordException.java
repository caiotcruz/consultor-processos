package com.consultorprocessos.user.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SamePasswordException extends DomainException {
    public SamePasswordException() {
        super("VALIDATION_ERROR",
            "A nova senha não pode ser igual à senha atual.",
            HttpStatus.BAD_REQUEST);
    }
}