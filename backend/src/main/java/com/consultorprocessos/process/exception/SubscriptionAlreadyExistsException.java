package com.consultorprocessos.process.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SubscriptionAlreadyExistsException extends DomainException {
    public SubscriptionAlreadyExistsException(String processNumber) {
        super("SUBSCRIPTION_ALREADY_EXISTS",
            "Você já acompanha o processo " + processNumber + ".",
            HttpStatus.CONFLICT);
    }
}