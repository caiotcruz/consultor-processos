package com.consultorprocessos.crawler.provider.stf;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.service.CrawlerPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        String digits = processNumber.replaceAll("[^0-9]", "");

        String url = stfBaseUrl + "/processos/listarProcessos.asp?numeroUnico=" + digits;

        log.debug("[STF] Consultando: processo={} url={}", processNumber, url);

        return pipeline.execute(COURT_CODE, processNumber, url, parser);
    }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}