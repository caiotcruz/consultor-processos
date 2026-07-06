package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.model.CrawlContext;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.RawResponseType;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JsoupCrawler {

    private static final int TIMEOUT_MS = 30_000;

    public RawResponse fetch(String url, CrawlContext context) {
        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(context.userAgent())
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true);

            if (context.hasCookies()) {
                connection.cookies(context.cookies());
            }

            Document doc = connection.get();
            int statusCode = connection.response().statusCode();

            log.debug("JsoupCrawler: {} → HTTP {}", url, statusCode);

            return new RawResponse(
                    doc.html(),
                    statusCode,
                    RawResponseType.HTML,
                    CrawlerStrategy.JSOUP
            );
        } catch (Exception e) {
            throw new com.consultorprocessos.crawler.exception
                    .CourtUnavailableException("JSOUP", e.getMessage());
        }
    }
}