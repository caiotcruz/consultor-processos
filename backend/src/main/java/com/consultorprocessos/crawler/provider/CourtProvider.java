package com.consultorprocessos.crawler.provider;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;

public interface CourtProvider {

    CrawlerSnapshot consultar(String processNumber);

    String getCourtCode();
}