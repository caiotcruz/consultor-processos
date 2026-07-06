package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.*;
import com.consultorprocessos.crawler.provider.stjrj.STJRJParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class STJRJParserTest {

    private final STJRJParser parser = new STJRJParser();

    @Test
    @DisplayName("deve parsear HTML normal com 3 movimentações")
    void shouldParseNormalHtml() {
        String html = loadFixture("stjrj/v1.0.0_processo_normal.html");

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).hasSize(3);
        assertThat(result.movements().get(0).rawDate()).isEqualTo("01/03/2025");
        assertThat(result.movements().get(0).rawDescription())
                .isEqualTo("Recurso recebido no segundo grau de jurisdição.");
    }

    @Test
    @DisplayName("deve retornar lista vazia para processo sem movimentações")
    void shouldReturnEmptyForNoMovements() {
        String html = loadFixture("stjrj/v1.0.0_sem_movimentacoes.html");

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("deve lançar ParseException quando div#listaMovimentacoes não for encontrada")
    void shouldThrowForMissingContainer() {
        assertThatThrownBy(() -> parser.parse(raw(
                "<html><body><div>sem movimentações</div></body></html>")))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("listaMovimentacoes");
    }

    @Test
    @DisplayName("versão deve ser '1.0.0' e código 'STJRJ'")
    void shouldHaveCorrectMetadata() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
        assertThat(parser.getCourtCode()).isEqualTo("STJRJ");
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
        return new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.HTTP);
    }
}