package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.model.RawResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BlockDetector {

    private static final List<String> BLOCK_SIGNALS = List.of(
            "captcha",
            "recaptcha",
            "acesso negado",
            "acesso bloqueado",
            "bloqueado",
            "muitas requisições",
            "too many requests",
            "rate limit"
    );

    public void check(RawResponse response) {
        if (response.httpStatusCode() == 403) {
            throw new CourtBlockedException("HTTP 403 Forbidden.");
        }
        if (response.httpStatusCode() == 429) {
            throw new CourtBlockedException("HTTP 429 Too Many Requests.");
        }

        if (response.content() != null) {
            String lower = response.content().toLowerCase();
            for (String signal : BLOCK_SIGNALS) {
                if (lower.contains(signal)) {
                    throw new CourtBlockedException(
                            "Sinal de bloqueio detectado no conteúdo: '" + signal + "'");
                }
            }
        }
    }
}