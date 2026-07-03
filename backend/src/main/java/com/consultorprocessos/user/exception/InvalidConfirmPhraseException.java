package com.consultorprocessos.user.exception;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidConfirmPhraseException extends DomainException {
    public InvalidConfirmPhraseException() {
        super("VALIDATION_ERROR",
            "Frase de confirmação incorreta. Digite exatamente: DELETAR MINHA CONTA",
            HttpStatus.BAD_REQUEST);
    }
}