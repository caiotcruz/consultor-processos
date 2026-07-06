package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.*;
import com.consultorprocessos.crawler.provider.eproc.EprocParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class EprocParserTest {

    private final EprocParser parser = new EprocParser();

    @Test
    @DisplayName("deve parsear HTML normal com 3 eventos")
    void shouldParseNormalHtml() {
        String html = loadFixture("eproc/v1.0.0_processo_normal.html");

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).hasSize(3);
        boolean hasIntimacao = result.movements().stream()
                .anyMatch(m -> m.rawDescription().contains("Intimação expedida"));
        assertThat(hasIntimacao).isTrue();
    }

    @Test
    @DisplayName("deve retornar lista vazia para processo sem eventos")
    void shouldReturnEmptyForNoEvents() {
        String html = loadFixture("eproc/v1.0.0_sem_movimentacoes.html");

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("deve lançar ParseException quando tabela não for encontrada")
    void shouldThrowForMissingTable() {
        assertThatThrownBy(() -> parser.parse(raw("<html><body><p>Sem tabela</p></body></html>")))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("tblEventos");
    }

    @Test
    @DisplayName("versão deve ser '1.0.0' e código 'EPROC'")
    void shouldHaveCorrectMetadata() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
        assertThat(parser.getCourtCode()).isEqualTo("EPROC");
    }

    private String loadFixture(String path) {
        try {
            return new String(getClass().getClassLoader()
                    .getResourceAsStream("fixtures/parsers/" + path)
                    .readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Fixture não encontrada: " + path, e);
        }
    }

    private RawResponse raw(String html) {
        return new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.JSOUP);
    }
}