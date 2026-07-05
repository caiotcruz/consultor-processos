package com.consultorprocessos.process.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SubscriptionNotFoundException extends DomainException {
    public SubscriptionNotFoundException() {
        super("NOT_FOUND",
            "Processo não encontrado ou não pertence ao usuário.",
            HttpStatus.NOT_FOUND);
    }
}