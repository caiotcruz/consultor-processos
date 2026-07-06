package com.consultorprocessos.crawler.provider;

import com.consultorprocessos.crawler.model.ParsedData;
import com.consultorprocessos.crawler.model.RawResponse;

public interface CourtParser {

    ParsedData parse(RawResponse rawResponse);

    String getVersion();

    String getCourtCode();
}