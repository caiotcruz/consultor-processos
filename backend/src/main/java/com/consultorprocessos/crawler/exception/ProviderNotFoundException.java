package com.consultorprocessos.crawler.exception;

public class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(String courtCode) {
        super("Nenhum CourtProvider registrado para o tribunal: " + courtCode +
              ". Verifique se o Provider está anotado com @Component.");
    }
}