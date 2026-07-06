package com.consultorprocessos.crawler.model;

import java.time.Instant;
import java.util.List;

public record CrawlerSnapshot(
        String          processNumber,
        String          courtCode,
        String          contentHash,
        String          rawContentJson,
        List<Movement>  movements,
        CrawlerStrategy strategyUsed,
        String          parserVersion,
        Instant         capturedAt
) {}