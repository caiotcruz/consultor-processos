package com.consultorprocessos.crawler.exception;

public class CourtUnavailableException extends RuntimeException {
    public CourtUnavailableException(String courtCode, String reason) {
        super("Tribunal " + courtCode + " indisponível: " + reason);
    }
}