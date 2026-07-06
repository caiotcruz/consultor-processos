package com.consultorprocessos.crawler.provider.eproc;

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
 * Parser do sistema eProc.
 *
 * Seletor principal: table#tblEventos
 *
 * Estrutura esperada:
 *   <table id="tblEventos">
 *     <tbody>
 *       <tr class="infraTrClara|infraTrEscura">
 *         <td>Seq</td>
 *         <td class="infraTd">DD/MM/YYYY</td>
 *         <td class="infraTd"><span class="eproc-descricao">...</span></td>
 *       </tr>
 *     </tbody>
 *   </table>
 *
 * Nota: eProc alternates between infraTrClara e infraTrEscura for row striping.
 */
@Component
@Slf4j
public class EprocParser implements CourtParser {

    private static final String PARSER_VERSION  = "1.0.0";
    private static final String COURT_CODE      = "EPROC";
    private static final String TABLE_SELECTOR  = "table#tblEventos";
    private static final String ROW_SELECTOR    = "tr.infraTrClara, tr.infraTrEscura";

    @Override
    public ParsedData parse(RawResponse rawResponse) {
        Document doc = Jsoup.parse(rawResponse.content());

        Element table = doc.selectFirst(TABLE_SELECTOR);
        if (table == null) {
            throw new ParseException(String.format(
                    "Seletor '%s' não encontrado no HTML do eProc. " +
                    "O layout pode ter mudado. Parser versão: %s",
                    TABLE_SELECTOR, PARSER_VERSION));
        }

        Elements rows = table.select(ROW_SELECTOR);
        List<RawMovement> movements = new ArrayList<>();

        for (Element row : rows) {
            Elements cells = row.select("td.infraTd");
            if (cells.size() < 2) {
                log.debug("[eProc] Linha com células insuficientes, ignorando.");
                continue;
            }

            String date;
            String desc;

            if (cells.size() >= 3) {
                date = cells.get(0).text().trim();
                desc = cells.get(1).text().trim();

                if (date.matches("\\d+")) {
                    date = cells.get(1).text().trim();
                    desc = cells.size() > 2 ? cells.get(2).text().trim() : "";
                }
            } else {
                date = cells.get(0).text().trim();
                desc = cells.get(1).text().trim();
            }

            if (!desc.isBlank()) {
                movements.add(new RawMovement(date, desc));
            }
        }

        String processNumber = extractProcessNumber(doc);
        log.debug("[eProc] Parseadas {} movimentações. processo={}",
                movements.size(), processNumber);

        return new ParsedData(processNumber, movements);
    }

    private String extractProcessNumber(Document doc) {
        Element el = doc.getElementById("lblNúmeroProcesso");
        return el != null ? el.text().trim() : "desconhecido";
    }

    @Override
    public String getVersion() { return PARSER_VERSION; }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}