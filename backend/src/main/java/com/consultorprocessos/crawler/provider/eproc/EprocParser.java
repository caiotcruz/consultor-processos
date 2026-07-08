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

@Component
@Slf4j
public class EprocParser implements CourtParser {

    private static final String PARSER_VERSION    = "1.0.0";
    private static final String COURT_CODE        = "EPROC";

    private static final String TABLE_SELECTOR    = "table.infraTable[summary='Assuntos']";

    private static final String ROW_SELECTOR      = "tr.infraTrClara, tr.infraTrEscura";

    private static final int COL_DATA_HORA  = 1;
    private static final int COL_DESCRICAO  = 2;

    @Override
    public ParsedData parse(RawResponse rawResponse) {
        Document doc = Jsoup.parse(rawResponse.content());

        Element table = doc.selectFirst(TABLE_SELECTOR);
        if (table == null) {
            throw new ParseException(String.format(
                    "Seletor '%s' não encontrado no HTML do eProc. " +
                    "O layout do portal pode ter mudado. Parser versão: %s",
                    TABLE_SELECTOR, PARSER_VERSION));
        }

        Elements rows = table.select(ROW_SELECTOR);
        List<RawMovement> movements = new ArrayList<>();

        for (Element row : rows) {
            Elements cells = row.select("td");

            if (cells.size() < 3) {
                log.debug("[eProc] Linha com {} células (esperado >= 3), ignorando.", cells.size());
                continue;
            }

            String rawDateFull = cells.get(COL_DATA_HORA).text().trim();
            String rawDate     = extractDateOnly(rawDateFull);

            String rawDescription = cells.get(COL_DESCRICAO).html().trim();

            if (!rawDescription.isBlank()) {
                movements.add(new RawMovement(rawDate, rawDescription));
            }
        }

        log.debug("[eProc] Parseados {} eventos.", movements.size());
        return new ParsedData("desconhecido", movements);
    }

    private String extractDateOnly(String rawDateFull) {
        if (rawDateFull == null || rawDateFull.isBlank()) return "";
        int spaceIndex = rawDateFull.indexOf(' ');
        return spaceIndex > 0 ? rawDateFull.substring(0, spaceIndex) : rawDateFull;
    }

    @Override
    public String getVersion() { return PARSER_VERSION; }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}