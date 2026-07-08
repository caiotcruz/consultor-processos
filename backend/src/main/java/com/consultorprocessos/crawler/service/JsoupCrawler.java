package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlContext;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.RawResponseType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

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

            if (statusCode >= 500) {
                throw new CourtUnavailableException(
                    "Servidor indisponível",
                    url
                );
            }


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

    public RawResponse fetchPost(String url, Map<String, String> formData, CrawlContext context) {
        try {
            Connection connection = Jsoup.connect(url)
                    .method(Connection.Method.POST)
                    .data(formData)
                    .userAgent(context.userAgent())
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true);

            if (context.hasCookies()) {
                connection.cookies(context.cookies());
            }

            Connection.Response response = connection.execute();
            Document doc = response.parse();
            int statusCode = response.statusCode();

            log.debug("JsoupCrawler POST: {} → HTTP {}", url, statusCode);

            if (statusCode >= 500) {
                throw new CourtUnavailableException("EPROC", "Servidor indisponível (HTTP " + statusCode + ")");
            }

            return new RawResponse(
                    doc.html(),
                    statusCode,
                    RawResponseType.HTML,
                    CrawlerStrategy.JSOUP
            );
        } catch (CourtUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new com.consultorprocessos.crawler.exception
                    .CourtUnavailableException("POST", e.getMessage());
        }
    }
}