package com.consultorprocessos.user.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCurrentPasswordException extends DomainException {
    public InvalidCurrentPasswordException() {
        super("INVALID_CREDENTIALS", "Senha atual incorreta.", HttpStatus.BAD_REQUEST);
    }
}