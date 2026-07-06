package com.consultorprocessos.crawler.exception;

public class CourtBlockedException extends RuntimeException {
    public CourtBlockedException(String reason) {
        super("Tribunal bloqueou o acesso: " + reason);
    }
}