package com.consultorprocessos.crawler.service;

import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.court.repository.CourtRepository;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.*;
import com.consultorprocessos.crawler.provider.CourtParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerPipeline {

    private final HttpCrawler          httpCrawler;
    private final JsoupCrawler         jsoupCrawler;
    private final BlockDetector        blockDetector;
    private final DelayStrategy        delayStrategy;
    private final CourtRateLimiter     rateLimiter;
    private final UserAgentRotator     userAgentRotator;
    private final HashGenerator        hashGenerator;
    private final ParsedDataNormalizer normalizer;
    private final CourtRepository      courtRepository;
    private final ObjectMapper         objectMapper;

    public CrawlerSnapshot execute(String courtCode,
                                   String processNumber,
                                   String url,
                                   CourtParser parser) {

        Court court = courtRepository.findByCode(courtCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Tribunal não encontrado: " + courtCode));

        long startTime = System.currentTimeMillis();

        rateLimiter.acquire(courtCode, court.getRateLimitPerMin());

        delayStrategy.apply(court.getMinDelayMs(), court.getMaxDelayMs());

        CrawlContext context = CrawlContext.defaultContext(userAgentRotator.next());

        RawResponse rawResponse = crawlWithFallback(url, context, courtCode);

        blockDetector.check(rawResponse);

        ParsedData parsedData = parser.parse(rawResponse);

        List<Movement> movements = normalizer.normalize(parsedData);

        String rawContentJson = buildCanonicalJson(processNumber, courtCode, movements);

        String contentHash = hashGenerator.sha256(rawContentJson);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Pipeline concluído: tribunal={} processo={} movimentos={} estratégia={} em {}ms",
                courtCode, processNumber, movements.size(),
                rawResponse.strategy(), duration);

        return new CrawlerSnapshot(
                processNumber,
                courtCode,
                contentHash,
                rawContentJson,
                movements,
                rawResponse.strategy(),
                parser.getVersion(),
                Instant.now()
        );
    }

    public CrawlerSnapshot executePostForm(String courtCode,
                                        String processNumber,
                                        String url,
                                        Map<String, String> formData,
                                        CourtParser parser) {

        Court court = courtRepository.findByCode(courtCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Tribunal não encontrado: " + courtCode));

        long startTime = System.currentTimeMillis();

        rateLimiter.acquire(courtCode, court.getRateLimitPerMin());

        delayStrategy.apply(court.getMinDelayMs(), court.getMaxDelayMs());

        CrawlContext context = CrawlContext.defaultContext(userAgentRotator.next());

        RawResponse rawResponse = jsoupCrawler.fetchPost(url, formData, context);

        blockDetector.check(rawResponse);

        ParsedData parsedData = parser.parse(rawResponse);

        List<Movement> movements = normalizer.normalize(parsedData);

        String rawContentJson = buildCanonicalJson(processNumber, courtCode, movements);
        String contentHash    = hashGenerator.sha256(rawContentJson);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Pipeline POST concluído: tribunal={} processo={} eventos={} em {}ms",
                courtCode, processNumber, movements.size(), duration);

        if (rawResponse.httpStatusCode() > 500){
                throw new CourtUnavailableException(
                        "Servidor indisponível",
                        url
                );
        }

        return new CrawlerSnapshot(
                processNumber,
                courtCode,
                contentHash,
                rawContentJson,
                movements,
                rawResponse.strategy(),
                parser.getVersion(),
                Instant.now()
        );
    }

    private RawResponse crawlWithFallback(String url, CrawlContext context,
                                          String courtCode) {
        try {
            RawResponse response = httpCrawler.fetch(url, context);
            return response;
        } catch (Exception httpEx) {
            log.warn("[{}] HttpCrawler falhou: {}. Tentando JsoupCrawler.",
                    courtCode, httpEx.getMessage());
        }

        try {
            return jsoupCrawler.fetch(url, context);
        } catch (Exception jsoupEx) {
            log.error("[{}] JsoupCrawler também falhou: {}.", courtCode, jsoupEx.getMessage());
            throw new CourtUnavailableException(courtCode,
                    "Todas as estratégias disponíveis falharam (HTTP, Jsoup).");
        }
    }

    private String buildCanonicalJson(String processNumber, String courtCode,
                                      List<Movement> movements) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("processNumber", processNumber);
            root.put("courtCode",     courtCode);

            ArrayNode movArray = root.putArray("movements");
            movements.stream()
                    .sorted(java.util.Comparator
                            .comparing((Movement m) -> m.date() != null ? m.date().toString() : "")
                            .thenComparing(m -> m.description() != null ? m.description() : ""))
                    .forEach(m -> {
                        ObjectNode mov = movArray.addObject();
                        mov.put("date",        m.date() != null ? m.date().toString() : "");
                        mov.put("description", m.description() != null
                                ? m.description().trim().toLowerCase() : "");
                    });

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar movimentos para JSON", e);
        }
    }

        public void applyPreCrawlStrategies(String courtCode) {
                Court court = courtRepository.findByCode(courtCode)
                        .orElseThrow(() -> new IllegalStateException(
                                "Tribunal não encontrado: " + courtCode));
                rateLimiter.acquire(courtCode, court.getRateLimitPerMin());
                delayStrategy.apply(court.getMinDelayMs(), court.getMaxDelayMs());      
        }


        public CrawlerSnapshot executeWithRawResponse(String courtCode,
                                                String processNumber,
                                                RawResponse rawResponse,
                                                CourtParser parser) {
                long startTime = System.currentTimeMillis();

                blockDetector.check(rawResponse);
                ParsedData parsedData = parser.parse(rawResponse);
                List<Movement> movements = normalizer.normalize(parsedData);
                String rawContentJson = buildCanonicalJson(processNumber, courtCode, movements);
                String contentHash    = hashGenerator.sha256(rawContentJson);

                long duration = System.currentTimeMillis() - startTime;
                log.info("Pipeline (rawResponse) concluído: tribunal={} processo={} movimentos={} em {}ms",
                        courtCode, processNumber, movements.size(), duration);

                if (rawResponse.httpStatusCode() > 500){
                        throw new CourtUnavailableException(
                                "Servidor indisponível",
                                "erro"
                        );
                }

                return new CrawlerSnapshot(
                        processNumber,
                        courtCode,
                        contentHash,
                        rawContentJson,
                        movements,
                        rawResponse.strategy(),
                        parser.getVersion(),
                        Instant.now()
                );
        }
}