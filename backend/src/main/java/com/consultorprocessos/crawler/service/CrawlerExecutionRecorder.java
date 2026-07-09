package com.consultorprocessos.crawler.service;

import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.crawler.entity.CrawlerExecution;
import com.consultorprocessos.crawler.entity.ParserVersion;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.repository.CrawlerExecutionRepository;
import com.consultorprocessos.process.entity.Process;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlerExecutionRecorder {

    private final CrawlerExecutionRepository repository;

    public CrawlerExecution record(Process       process,
                                   Court         court,
                                   CrawlerStrategy strategy,
                                   boolean       success,
                                   long          durationMs,
                                   Integer       httpStatusCode,
                                   String        errorType,
                                   String        errorMessage,
                                   ParserVersion parserVersion) {
        CrawlerExecution exec = new CrawlerExecution();
        exec.setProcess(process);
        exec.setCourt(court);
        exec.setStrategy(strategy);
        exec.setSuccess(success);
        exec.setDurationMs(durationMs);
        exec.setHttpStatusCode(httpStatusCode);
        exec.setErrorType(errorType);
        exec.setErrorMessage(truncate(errorMessage, 500));
        exec.setParserVersion(parserVersion);

        CrawlerExecution saved = repository.save(exec);

        if (!success) {
            log.warn("CrawlerExecution registrada (falha): process={} court={} strategy={} " +
                     "durationMs={} errorType={} errorMessage={}",
                    process.getProcessNumber(), court.getCode(), strategy,
                    durationMs, errorType, truncate(errorMessage, 100));
        } else {
            log.debug("CrawlerExecution registrada (sucesso): process={} court={} durationMs={}",
                    process.getProcessNumber(), court.getCode(), durationMs);
        }

        return saved;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}