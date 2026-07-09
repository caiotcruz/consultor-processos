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

@Component
@Slf4j
public class STFParser implements CourtParser {

    private static final String PARSER_VERSION        = "1.0.0";
    private static final String COURT_CODE            = "STF";

    private static final String CONTAINER_SELECTOR   = "div.processo-andamentos";

    private static final String ITEM_SELECTOR        = "li";

    private static final String DATE_SELECTOR        = "div.andamento-data";
    private static final String NOME_SELECTOR        = "h5.andamento-nome";

    private static final String DETALHE_CONTAINER    = "div.andamento-detalhe";
    private static final String DETALHE_SELECTOR     = "div.col-md-9.p-0";

    @Override
    public ParsedData parse(RawResponse rawResponse) {
        Document doc = Jsoup.parse(rawResponse.content());

        Element container = doc.selectFirst(CONTAINER_SELECTOR);
        if (container == null) {
            throw new ParseException(String.format(
                    "Seletor '%s' não encontrado. " +
                    "O layout do portal STF pode ter mudado. Parser versão: %s",
                    CONTAINER_SELECTOR, PARSER_VERSION));
        }

        Elements items = container.select(ITEM_SELECTOR);
        List<RawMovement> movements = new ArrayList<>();

        for (Element item : items) {
            Element dateEl = item.selectFirst(DATE_SELECTOR);
            Element nomeEl = item.selectFirst(NOME_SELECTOR);

            if (nomeEl == null) {
                log.debug("[STF] Item sem h5.andamento-nome, ignorando.");
                continue;
            }

            String date = dateEl != null ? dateEl.text().trim() : "";
            String nome = nomeEl.text().trim();
            String detalhe = extractDetalhe(item);

            String fullDescription = buildDescription(nome, detalhe);

            if (!fullDescription.isBlank()) {
                movements.add(new RawMovement(date, fullDescription));
            }
        }

        log.debug("[STF] Parseados {} andamentos.", movements.size());
        return new ParsedData("desconhecido", movements);
    }

    private String extractDetalhe(Element item) {
        Element detalheContainer = item.selectFirst(DETALHE_CONTAINER);
        if (detalheContainer == null) return "";

        for (Element div : detalheContainer.select(DETALHE_SELECTOR)) {
            String text = div.text().trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String buildDescription(String nome, String detalhe) {
        if (detalhe.isBlank()) {
            return nome;
        }
        if (nome.isBlank()) {
            return detalhe;
        }
        return nome + " — " + detalhe;
    }

    @Override
    public String getVersion() { return PARSER_VERSION; }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}