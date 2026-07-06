// src/main/java/com/consultorprocessos/crawler/provider/stjrj/STJRJParser.java
package com.consultorprocessos.crawler.provider.stjrj;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.ParsedData;
import com.consultorprocessos.crawler.model.RawMovement;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.provider.CourtParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser do TJRJ (Tribunal de Justiça do Estado do Rio de Janeiro).
 *
 * Seletor principal: div#listaMovimentacoes
 *
 * Estrutura esperada:
 *   <div id="listaMovimentacoes">
 *     <div class="movimentacao">
 *       <div class="dataMovimentacao">DD/MM/YYYY</div>
 *       <div class="descricaoMovimentacao">Texto</div>
 *     </div>
 *   </div>
 */
@Component
@Slf4j
public class STJRJParser implements CourtParser {

    private static final String PARSER_VERSION = "1.0.0";
    private static final String COURT_CODE     = "STJRJ";
    private static final String LIST_SELECTOR  = "div#listaMovimentacoes";
    private static final String ITEM_SELECTOR  = "div.movimentacao";
    private static final String DATE_SELECTOR  = "div.dataMovimentacao, .dataMovimentacao";
    private static final String DESC_SELECTOR  = "div.descricaoMovimentacao, .descricaoMovimentacao";

    @Override
    public ParsedData parse(RawResponse rawResponse) {
        Document doc = Jsoup.parse(rawResponse.content());

        Element listContainer = doc.selectFirst(LIST_SELECTOR);
        if (listContainer == null) {
            throw new ParseException(String.format(
                    "Seletor '%s' não encontrado no HTML do TJRJ. " +
                    "O layout pode ter mudado. Parser versão: %s",
                    LIST_SELECTOR, PARSER_VERSION));
        }

        Elements items = listContainer.select(ITEM_SELECTOR);
        List<RawMovement> movements = new ArrayList<>();

        for (Element item : items) {
            Element dateEl = item.selectFirst(DATE_SELECTOR);
            Element descEl = item.selectFirst(DESC_SELECTOR);

            if (dateEl == null || descEl == null) {
                log.debug("[STJRJ] Item sem elementos esperados, ignorando.");
                continue;
            }

            String date = dateEl.text().trim();
            String desc = descEl.text().trim();

            if (!desc.isBlank()) {
                movements.add(new RawMovement(date, desc));
            }
        }

        String processNumber = extractProcessNumber(doc);
        log.debug("[STJRJ] Parseadas {} movimentações. processo={}",
                movements.size(), processNumber);

        return new ParsedData(processNumber, movements);
    }

    private String extractProcessNumber(Document doc) {
        Element el = doc.selectFirst("span.numProcesso");
        return el != null ? el.text().trim() : "desconhecido";
    }

    @Override
    public String getVersion() { return PARSER_VERSION; }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}