package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.ParsedData;
import com.consultorprocessos.crawler.model.RawResponse;
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
    @DisplayName("deve parsear fixture normal com 3 andamentos")
    void shouldParseNormalFixture() {
        ParsedData result = parser.parse(loadFixture("stf/v1.0.0_processo_normal.html"));

        assertThat(result.movements()).hasSize(3);
    }

    @Test
    @DisplayName("primeiro andamento deve ter data 11/02/2026")
    void shouldExtractDateFromFirstAndamento() {
        ParsedData result = parser.parse(loadFixture("stf/v1.0.0_processo_normal.html"));

        assertThat(result.movements().get(0).rawDate()).isEqualTo("11/02/2026");
    }

    @Test
    @DisplayName("andamento com detalhe complementar deve concatenar descrição")
    void shouldConcatenateDetalheWhenPresent() {
        ParsedData result = parser.parse(loadFixture("stf/v1.0.0_processo_normal.html"));

        String desc = result.movements().get(0).rawDescription();
        assertThat(desc).contains("Baixa ao arquivo do STF, Guia nº");
        assertThat(desc).contains("Guia: 4418/2026");
        assertThat(desc).contains(" — ");
    }

    @Test
    @DisplayName("andamento sem detalhe complementar deve usar apenas o nome")
    void shouldUseOnlyNomeWhenDetalheIsEmpty() {
        ParsedData result = parser.parse(loadFixture("stf/v1.0.0_processo_normal.html"));

        String desc = result.movements().get(1).rawDescription();
        assertThat(desc).isEqualTo("Transitado em julgado");
        assertThat(desc).doesNotContain(" — ");
    }

    @Test
    @DisplayName("andamento com detalhe deve preservar texto do detalhe")
    void shouldPreserveDetalheText() {
        ParsedData result = parser.parse(loadFixture("stf/v1.0.0_processo_normal.html"));

        String desc = result.movements().get(2).rawDescription();
        assertThat(desc).contains("Acórdão publicado no DJe");
        assertThat(desc).contains("DJe nº 275");
    }

    @Test
    @DisplayName("deve retornar lista vazia quando não há andamentos")
    void shouldReturnEmptyForNoAndamentos() {
        ParsedData result = parser.parse(loadFixture("stf/v1.0.0_sem_movimentacoes.html"));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("deve lançar ParseException quando div.processo-andamentos não existir")
    void shouldThrowWhenContainerMissing() {
        RawResponse response = raw("<html><body><p>Página sem andamentos</p></body></html>");

        assertThatThrownBy(() -> parser.parse(response))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("processo-andamentos");
    }

    @Test
    @DisplayName("deve ignorar li sem h5.andamento-nome")
    void shouldIgnoreLiWithoutNome() {
        String html = """
                <!DOCTYPE html>
                <html><body>
                <div class="processo-andamentos m-t-8">
                  <ul>
                    <li>
                      <div class="andamento-detalhe">
                        <div class="andamento-data">01/01/2025</div>
                        <!-- sem h5.andamento-nome -->
                      </div>
                    </li>
                  </ul>
                </div>
                </body></html>
                """;

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("versão deve ser '1.0.0' e código 'STF'")
    void shouldHaveCorrectMetadata() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
        assertThat(parser.getCourtCode()).isEqualTo("STF");
    }

    private RawResponse loadFixture(String path) {
        try {
            String html = new String(
                    getClass().getClassLoader()
                            .getResourceAsStream("fixtures/parsers/" + path)
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
            return raw(html);
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Fixture não encontrada: fixtures/parsers/" + path, e);
        }
    }

    private RawResponse raw(String html) {
        return new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.HTTP);
    }
}