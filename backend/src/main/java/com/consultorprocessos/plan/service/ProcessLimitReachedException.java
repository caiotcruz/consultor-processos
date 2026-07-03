package com.consultorprocessos.plan.service;

import com.consultorprocessos.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProcessLimitReachedException extends DomainException {

    public ProcessLimitReachedException(int limit) {
        super("PROCESS_LIMIT_REACHED",
            "Você atingiu o limite de " + limit + " processo(s) do seu plano. " +
            "Faça upgrade para continuar adicionando processos.",
            HttpStatus.UNPROCESSABLE_ENTITY);
    }
}