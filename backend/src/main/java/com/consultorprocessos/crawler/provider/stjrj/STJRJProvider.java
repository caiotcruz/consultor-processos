// src/main/java/com/consultorprocessos/crawler/provider/stjrj/STJRJProvider.java
package com.consultorprocessos.crawler.provider.stjrj;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.service.CrawlerPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provider do TJRJ (Tribunal de Justiça do Estado do Rio de Janeiro).
 * Denominado STJRJ no sistema para corresponder ao código cadastrado no banco.
 *
 * Estratégia primária: HTTP Direto (consultas públicas servem HTML estático).
 * Fallback: Jsoup (tratado automaticamente pelo CrawlerPipeline).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class STJRJProvider implements CourtProvider {

    private static final String COURT_CODE = "STJRJ";

    private final CrawlerPipeline pipeline;
    private final STJRJParser      parser;

    @Value("${app.courts.stjrj.base-url}")
    private String stjrjBaseUrl;

    @Override
    public CrawlerSnapshot consultar(String processNumber) {
        String url = stjrjBaseUrl +
                     "/consultaProcessual/buscaProcesso.do" +
                     "?numProcesso=" + processNumber;

        log.debug("[STJRJ] Consultando: {}", url);
        return pipeline.execute(COURT_CODE, processNumber, url, parser);
    }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}