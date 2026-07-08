package com.consultorprocessos.crawler.provider.stjrj;

import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.RawResponseType;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.service.CrawlerPipeline;
import com.consultorprocessos.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class STJRJProvider implements CourtProvider {

    private static final String COURT_CODE       = "STJRJ";
    private static final String API_BASE_PATH    = "/consultaprocessual/api/processos";
    private static final String BUSCA_PATH       = "/por-numeracao-unica";
    private static final String MOVIMENTOS_PATH  = "/por-numero/movimentos";
    private static final int    TIPO_PRIMEIRO_GRAU = 1;

    private final CrawlerPipeline pipeline;
    private final STJRJParser      parser;
    private final ObjectMapper     objectMapper;

    @Value("${app.courts.stjrj.base-url}")
    private String tjrjBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public CrawlerSnapshot consultar(String processNumber) {
        pipeline.applyPreCrawlStrategies(COURT_CODE);

        log.debug("[TJRJ] Consultando processo: {}", processNumber);

        String codigoProcesso = resolveCodigoProcesso(processNumber);

        String movimentosJson = fetchMovimentos(codigoProcesso);

        RawResponse rawResponse = new RawResponse(
                movimentosJson,
                200,
                RawResponseType.JSON,
                CrawlerStrategy.HTTP
        );

        return pipeline.executeWithRawResponse(COURT_CODE, processNumber, rawResponse, parser);
    }

    private String resolveCodigoProcesso(String cnj) {
        String url  = tjrjBaseUrl + API_BASE_PATH + BUSCA_PATH;
        String body = buildBuscaBody(cnj);

        log.debug("[TJRJ] Passo 1 - Buscando codigoProcesso: url={} cnj={}", url, cnj);

        String responseJson = postJson(url, body);

        try {
            JsonNode array = objectMapper.readTree(responseJson);
            if (!array.isArray()) {
                throw new CourtUnavailableException(COURT_CODE,
                        "Resposta inesperada do endpoint por-numeracao-unica: não é array.");
            }

            for (JsonNode entry : array) {
                if (entry.path("tipoProcesso").asInt() == TIPO_PRIMEIRO_GRAU) {
                    String codigoProcesso = entry.path("numProcesso").asText("");
                    if (!codigoProcesso.isBlank()) {
                        log.debug("[TJRJ] codigoProcesso resolvido: {}", codigoProcesso);
                        return codigoProcesso;
                    }
                }
            }

            throw new NotFoundException(
                    "Processo " + cnj + " não encontrado em 1º grau no TJRJ.");

        } catch (NotFoundException | CourtUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CourtUnavailableException(COURT_CODE,
                    "Falha ao parsear resposta do por-numeracao-unica: " + e.getMessage());
        }
    }

    private String fetchMovimentos(String codigoProcesso) {
        String url  = tjrjBaseUrl + API_BASE_PATH + MOVIMENTOS_PATH;
        String body = buildMovimentosBody(codigoProcesso);

        log.debug("[TJRJ] Passo 2 - Buscando movimentos: codigoProcesso={}", codigoProcesso);

        return postJson(url, body);
    }

    private String postJson(String url, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept",       "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            log.debug("[TJRJ] POST {} → HTTP {}", url, response.statusCode());

            if (response.statusCode() == 403) {
                throw new com.consultorprocessos.crawler.exception
                        .CourtBlockedException("HTTP 403 em " + url);
            }
            if (response.statusCode() == 429) {
                throw new com.consultorprocessos.crawler.exception
                        .CourtBlockedException("HTTP 429 em " + url);
            }
            if (response.statusCode() >= 500) {
                throw new CourtUnavailableException(COURT_CODE,
                        "HTTP " + response.statusCode() + " em " + url);
            }

            if (response.statusCode() > 500){
                throw new CourtUnavailableException(
                                "Servidor indisponível",
                                "erro"
                        );
            }

            return response.body();

        } catch (com.consultorprocessos.crawler.exception.CourtBlockedException |
                 CourtUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CourtUnavailableException(COURT_CODE,
                    "Erro na chamada HTTP: " + e.getMessage());
        }
    }

    private String buildBuscaBody(String cnj) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("codigoCNJ", cnj);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar body da busca", e);
        }
    }

    private String buildMovimentosBody(String codigoProcesso) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("tipoProcesso",        String.valueOf(TIPO_PRIMEIRO_GRAU));
            body.put("codigoProcesso",       codigoProcesso);
            body.put("indProcVolumoso",      "N");
            body.putNull("ultimaOrdemExibida");
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar body de movimentos", e);
        }
    }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}