package com.consultorprocessos.crawler.provider.stjrj;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.ParsedData;
import com.consultorprocessos.crawler.model.RawMovement;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.provider.CourtParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class STJRJParser implements CourtParser {

    private static final String PARSER_VERSION     = "1.0.0";
    private static final String COURT_CODE         = "STJRJ";
    private static final String MOVIMENTOS_KEY     = "movimentosProc";
    private static final String FIELD_DT_MOVIMENTO = "dtMovimento";
    private static final String FIELD_DESCR_MOV    = "descrMov";
    private static final String FIELD_DESCRICAO    = "descricao";

    private final ObjectMapper objectMapper;

    @Override
    public ParsedData parse(RawResponse rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse.content());

            JsonNode movimentosNode = root.path(MOVIMENTOS_KEY);
            if (movimentosNode.isMissingNode() || !movimentosNode.isArray()) {
                throw new ParseException(String.format(
                        "Campo '%s' não encontrado ou inválido no JSON do TJRJ. " +
                        "Parser versão: %s",
                        MOVIMENTOS_KEY, PARSER_VERSION));
            }

            List<RawMovement> movements = new ArrayList<>();
            for (JsonNode mov : movimentosNode) {
                String date      = mov.path(FIELD_DT_MOVIMENTO).asText("").trim();
                String descrMov  = mov.path(FIELD_DESCR_MOV).asText("").trim();
                String descricao = extractDescricao(mov);

                String fullDescription = buildDescription(descrMov, descricao);

                if (!fullDescription.isBlank()) {
                    movements.add(new RawMovement(date, fullDescription));
                }
            }

            log.debug("[TJRJ] Parseados {} movimentos.", movements.size());
            return new ParsedData("desconhecido", movements);

        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException(
                    "Falha ao parsear JSON do TJRJ: " + e.getMessage());
        }
    }

    private String extractDescricao(JsonNode mov) {
        JsonNode node = mov.path(FIELD_DESCRICAO);
        if (node.isNull() || node.isMissingNode()) return "";
        return node.asText("").trim();
    }

    private String buildDescription(String descrMov, String descricao) {
        if (descricao.isBlank()) return descrMov;
        if (descrMov.isBlank())  return descricao;
        return descrMov + " — " + descricao;
    }

    @Override
    public String getVersion() { return PARSER_VERSION; }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}