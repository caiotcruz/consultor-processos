package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.ParsedData;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.RawResponseType;
import com.consultorprocessos.crawler.provider.stf.STFParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class STFParserTest {

    private final STFParser parser = new STFParser();

    @Test
    @DisplayName("deve parsear HTML normal com 3 movimentações")
    void shouldParseNormalHtml() {
        String html = loadFixture("stf/v1.0.0_processo_normal.html");

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).hasSize(3);
        assertThat(result.movements().get(0).rawDate()).isEqualTo("15/03/2025");
        assertThat(result.movements().get(0).rawDescription())
                .isEqualTo("Conclusos ao relator para deliberação.");
        assertThat(result.movements().get(1).rawDate()).isEqualTo("20/01/2025");
    }

    @Test
    @DisplayName("deve retornar lista vazia para processo sem movimentações")
    void shouldReturnEmptyForNoMovements() {
        String html = loadFixture("stf/v1.0.0_sem_movimentacoes.html");

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("deve lançar ParseException quando seletor principal não for encontrado")
    void shouldThrowParseExceptionForUnknownHtml() {
        String html = "<html><body><p>Página sem tabela de movimentações</p></body></html>";

        assertThatThrownBy(() -> parser.parse(raw(html)))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("tabelaTodasMovimentacoes");
    }

    @Test
    @DisplayName("deve ter versão '1.0.0' e código 'STF'")
    void shouldHaveCorrectVersionAndCode() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
        assertThat(parser.getCourtCode()).isEqualTo("STF");
    }

    @Test
    @DisplayName("deve ignorar linhas sem células de data ou descrição")
    void shouldIgnoreRowsWithoutRequiredCells() {
        String html = loadFixture("stf/v1.0.0_processo_normal.html")
                .replace("class=\"andamento-data\"", "class=\"outro-campo\"");

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).isEmpty();
    }


    private String loadFixture(String path) {
        try {
            return new String(
                    getClass().getClassLoader()
                            .getResourceAsStream("fixtures/parsers/" + path)
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Fixture não encontrada: fixtures/parsers/" + path, e);
        }
    }

    private RawResponse raw(String html) {
        return new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.HTTP);
    }
}