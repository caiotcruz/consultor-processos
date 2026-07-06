package com.consultorprocessos.crawler.provider.mock;

import com.consultorprocessos.crawler.model.*;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.service.BlockDetector;
import com.consultorprocessos.crawler.service.HashGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class MockCourtProvider implements CourtProvider {

    private static final String MOCK_PARSER_VERSION = "1.0.0";
    private static final String COURT_CODE          = "MOCK";

    @Value("${app.courts.mock-base-url:http://localhost:8080}")
    private String mockBaseUrl;

    private final HashGenerator hashGenerator;
    private final BlockDetector blockDetector;
    private final ObjectMapper  objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public CrawlerSnapshot consultar(String processNumber) {
        return consultarCourt("STF", processNumber);
    }

    public CrawlerSnapshot consultarCourt(String courtCode, String processNumber) {
        long   startTime = System.currentTimeMillis();
        String url       = mockBaseUrl + "/mock/" + courtCode + "/" + processNumber;

        log.debug("[MOCK] Consultando: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "text/html")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            RawResponse rawResponse = new RawResponse(
                    response.body(),
                    response.statusCode(),
                    RawResponseType.HTML,
                    CrawlerStrategy.HTTP
            );

            blockDetector.check(rawResponse);

            List<Movement> movements = parseMovements(response.body());

            String rawContentJson = buildJson(processNumber, courtCode, movements);

            CrawlerSnapshot snapshot = new CrawlerSnapshot(
                    processNumber,
                    courtCode,
                    hashGenerator.sha256(rawContentJson),
                    rawContentJson,
                    movements,
                    CrawlerStrategy.HTTP,
                    MOCK_PARSER_VERSION,
                    Instant.now()
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("[MOCK] Consulta concluída: processo={} tribunal={} movimentos={} em {}ms",
                    processNumber, courtCode, movements.size(), duration);

            return snapshot;

        } catch (Exception e) {
            throw new com.consultorprocessos.crawler.exception
                    .CourtUnavailableException(courtCode, e.getMessage());
        }
    }

    private List<Movement> parseMovements(String html) {
        Document doc      = Jsoup.parse(html);
        Elements elements = doc.select("div.movement");
        List<Movement> movements = new ArrayList<>();

        for (org.jsoup.nodes.Element el : elements) {
            String dateStr  = el.select("span.movement-date").text().trim();
            String desc     = el.select("span.movement-description").text().trim();

            LocalDate date = null;
            try {
                if (!dateStr.isBlank()) {
                    date = LocalDate.parse(dateStr);
                }
            } catch (Exception e) {
                log.warn("[MOCK] Falha ao parsear data '{}': {}", dateStr, e.getMessage());
            }

            if (!desc.isBlank()) {
                movements.add(new Movement(date, desc));
            }
        }

        return movements;
    }

    private String buildJson(String processNumber, String courtCode,
                             List<Movement> movements) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("processNumber", processNumber);
            root.put("courtCode",     courtCode);

            ArrayNode movArray = root.putArray("movements");
            for (Movement m : movements) {
                ObjectNode movNode = movArray.addObject();
                movNode.put("date",        m.date() != null ? m.date().toString() : "");
                movNode.put("description", m.description() != null ? m.description().trim() : "");
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar snapshot do mock", e);
        }
    }

    @Override
    public String getCourtCode() {
        return COURT_CODE;
    }
}