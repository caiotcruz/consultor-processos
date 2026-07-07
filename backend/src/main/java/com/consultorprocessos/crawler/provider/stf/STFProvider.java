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
 * Fluxo de consulta:
 *   1. Converte número CNJ para apenas dígitos (ex: 0001234-55.2020.8.26.0001 → 00012345520208260001)
 *   2. GET /processos/listarProcessos.asp?numeroUnico={dígitos}
 *   3. O portal retorna 302 → redirect para /processos/detalhe.asp?incidente={id}
 *   4. O HttpClient segue o redirect automaticamente (Redirect.NORMAL)
 *   5. O HTML final é entregue ao STFParser
 *
 * Rate limit: 5 req/min | Delay: 2000–5000ms (configurados no DB via V100 seed)
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
        // Strip para apenas dígitos — formato exigido pelo parâmetro numeroUnico
        String digits = processNumber.replaceAll("[^0-9]", "");

        String url = stfBaseUrl + "/processos/listarProcessos.asp?numeroUnico=" + digits;

        log.debug("[STF] Consultando: processo={} url={}", processNumber, url);

        // O HttpClient tem Redirect.NORMAL → segue o 302 automaticamente
        // e o HTML da página detalhe.asp é entregue ao parser
        return pipeline.execute(COURT_CODE, processNumber, url, parser);
    }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}