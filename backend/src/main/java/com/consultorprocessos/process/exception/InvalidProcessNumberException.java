package com.consultorprocessos.process.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidProcessNumberException extends DomainException {
    public InvalidProcessNumberException(String detail) {
        super("PROCESS_NUMBER_INVALID",
            "Número de processo inválido. " + detail,
            HttpStatus.BAD_REQUEST);
    }
}