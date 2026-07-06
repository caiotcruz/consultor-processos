package com.consultorprocessos.crawler.model;

import java.util.Map;

public record CrawlContext(
        String              userAgent,
        Map<String, String> cookies,
        String              waitForSelector
) {
    public boolean hasCookies() {
        return cookies != null && !cookies.isEmpty();
    }

    public static CrawlContext defaultContext(String userAgent) {
        return new CrawlContext(userAgent, Map.of(), null);
    }
}