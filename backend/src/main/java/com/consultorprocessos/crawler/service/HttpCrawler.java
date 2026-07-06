package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.model.CrawlContext;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.RawResponseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Slf4j
public class HttpCrawler {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public RawResponse fetch(String url, CrawlContext context) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent",      context.userAgent())
                    .header("Accept",          "text/html,application/json")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                    .timeout(Duration.ofSeconds(30))
                    .GET();

            if (context.hasCookies()) {
                String cookieHeader = buildCookieHeader(context.cookies());
                builder.header("Cookie", cookieHeader);
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            log.debug("HttpCrawler: {} → HTTP {}", url, response.statusCode());

            return new RawResponse(
                    response.body(),
                    response.statusCode(),
                    RawResponseType.HTML,
                    CrawlerStrategy.HTTP
            );
        } catch (Exception e) {
            throw new com.consultorprocessos.crawler.exception
                    .CourtUnavailableException("HTTP", e.getMessage());
        }
    }

    private String buildCookieHeader(java.util.Map<String, String> cookies) {
        return cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}