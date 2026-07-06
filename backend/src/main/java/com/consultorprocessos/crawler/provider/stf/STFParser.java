// src/main/java/com/consultorprocessos/crawler/provider/stf/STFParser.java
package com.consultorprocessos.crawler.provider.stf;

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
 * Parser do portal do STF.
 *
 * Seletor principal: table#tabelaTodasMovimentacoes
 *
 * Estrutura esperada:
 *   <table id="tabelaTodasMovimentacoes">
 *     <tbody>
 *       <tr class="andamento-linha">
 *         <td class="andamento-data">DD/MM/YYYY</td>
 *         <td class="andamento-descricao">Descrição da movimentação</td>
 *       </tr>
 *     </tbody>
 *   </table>
 *
 * IMPORTANTE: Quando o portal do STF alterar seu layout, este parser precisará
 * ser atualizado. Incrementar a versão e registrar nova ParserVersion no banco.
 */
@Component
@Slf4j
public class STFParser implements CourtParser {

    private static final String PARSER_VERSION    = "1.0.0";
    private static final String COURT_CODE        = "STF";
    private static final String TABLE_SELECTOR    = "table#tabelaTodasMovimentacoes";
    private static final String ROW_SELECTOR      = "tr.andamento-linha";
    private static final String DATE_SELECTOR     = "td.andamento-data";
    private static final String DESC_SELECTOR     = "td.andamento-descricao";

    @Override
    public ParsedData parse(RawResponse rawResponse) {
        Document doc = Jsoup.parse(rawResponse.content());

        Element table = doc.selectFirst(TABLE_SELECTOR);
        if (table == null) {
            throw new ParseException(String.format(
                    "Seletor '%s' não encontrado no HTML do STF. " +
                    "O layout do portal pode ter mudado. Parser versão: %s",
                    TABLE_SELECTOR, PARSER_VERSION));
        }

        Elements rows = table.select(ROW_SELECTOR);
        List<RawMovement> movements = new ArrayList<>();

        for (Element row : rows) {
            Element dateEl = row.selectFirst(DATE_SELECTOR);
            Element descEl = row.selectFirst(DESC_SELECTOR);

            if (dateEl == null || descEl == null) {
                log.debug("[STF] Linha sem células esperadas, ignorando: {}", row.html());
                continue;
            }

            String date = dateEl.text().trim();
            String desc = descEl.text().trim();

            if (!desc.isBlank()) {
                movements.add(new RawMovement(date, desc));
            }
        }

        String processNumber = extractProcessNumber(doc);

        log.debug("[STF] Parseadas {} movimentações. processo={}",
                movements.size(), processNumber);

        return new ParsedData(processNumber, movements);
    }

    private String extractProcessNumber(Document doc) {
        Element heading = doc.selectFirst("h2.heading-4");
        return heading != null ? heading.text().trim() : "desconhecido";
    }

    @Override
    public String getVersion() { return PARSER_VERSION; }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}