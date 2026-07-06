// src/main/java/com/consultorprocessos/crawler/provider/stf/STFProvider.java
package com.consultorprocessos.crawler.provider.stf;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.service.CrawlerPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provider do Supremo Tribunal Federal.
 *
 * Estratégia primária: HTTP Direto (portal serve HTML estático).
 * Fallback: Jsoup (tratado automaticamente pelo CrawlerPipeline).
 *
 * URL de consulta: {stf.base-url}/processos/detalhe.asp?numeroProcesso={processNumber}
 *
 * Rate limit: 5 req/min | Delay: 2000–5000ms (configurados em courts.rate_limit_per_min)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class STFProvider implements CourtProvider {

    private static final String COURT_CODE = "STF";

    private final CrawlerPipeline pipeline;
    private final STFParser        parser;

    @Value("${app.courts.stf.base-url}")
    private String stfBaseUrl;

    @Override
    public CrawlerSnapshot consultar(String processNumber) {
        String url = stfBaseUrl + "/processos/detalhe.asp?numeroProcesso="
                     + processNumber;

        log.debug("[STF] Consultando: {}", url);
        return pipeline.execute(COURT_CODE, processNumber, url, parser);
    }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}