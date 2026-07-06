package com.consultorprocessos.crawler.model;

public record RawResponse(
        String          content,
        int             httpStatusCode,
        RawResponseType type,
        CrawlerStrategy strategy
) {}