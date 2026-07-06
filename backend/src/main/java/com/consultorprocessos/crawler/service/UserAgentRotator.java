package com.consultorprocessos.crawler.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UserAgentRotator {

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",

            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",

            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) " +
            "Gecko/20100101 Firefox/123.0",

            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_3) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15",

            "Mozilla/5.0 (X11; Linux x86_64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    );

    private final AtomicInteger index = new AtomicInteger(0);

    public String next() {
        int i = index.getAndIncrement() % USER_AGENTS.size();
        return USER_AGENTS.get(i);
    }
}