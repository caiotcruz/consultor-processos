package com.consultorprocessos.crawler.provider.eproc;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.service.CrawlerPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provider do eProc (Processo Eletrônico dos Tribunais Federais).
 *
 * Estratégia primária: Jsoup (lida melhor com sessões e headers específicos do eProc).
 * Fallback: HTTP Direto (tratado automaticamente pelo CrawlerPipeline).
 *
 * Nota: para processos restritos, o eProc pode exigir login.
 * Esta versão suporta apenas consultas públicas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EprocProvider implements CourtProvider {

    private static final String COURT_CODE = "EPROC";

    private final CrawlerPipeline pipeline;
    private final EprocParser      parser;

    @Value("${app.courts.eproc.base-url}")
    private String eprocBaseUrl;

    @Override
    public CrawlerSnapshot consultar(String processNumber) {
        String url = eprocBaseUrl +
                     "/epaprocesso/servlet/ControladorPublico" +
                     "?numeroDoProcesso=" + processNumber +
                     "&acao=abrirLinkProcesso";

        log.debug("[eProc] Consultando: {}", url);
        return pipeline.execute(COURT_CODE, processNumber, url, parser);
    }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}