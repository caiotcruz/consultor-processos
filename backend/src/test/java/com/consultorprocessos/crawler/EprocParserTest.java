package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.ParsedData;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.RawResponseType;
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
    @DisplayName("deve parsear fixture normal com 4 eventos")
    void shouldParseNormalFixture() {
        ParsedData result = parser.parse(loadFixture("eproc/v1.0.0_processo_normal.html"));

        assertThat(result.movements()).hasSize(4);
    }

    @Test
    @DisplayName("deve extrair data sem a hora do campo Data/Hora")
    void shouldExtractDateWithoutTime() {
        ParsedData result = parser.parse(loadFixture("eproc/v1.0.0_processo_normal.html"));

        assertThat(result.movements().get(0).rawDate()).isEqualTo("06/07/2026");
        assertThat(result.movements().get(0).rawDate()).doesNotContain(":");
    }

    @Test
    @DisplayName("deve extrair descrição do primeiro evento corretamente")
    void shouldExtractFirstEventDescription() {
        ParsedData result = parser.parse(loadFixture("eproc/v1.0.0_processo_normal.html"));

        assertThat(result.movements().get(0).rawDescription())
                .isEqualTo("Conclusos para decisão/despacho");
    }

    @Test
    @DisplayName("deve extrair descrição com <br/> (normalização fica a cargo do ParsedDataNormalizer)")
    void shouldExtractDescriptionWithBrTags() {
        ParsedData result = parser.parse(loadFixture("eproc/v1.0.0_processo_normal.html"));

        String desc = result.movements().get(2).rawDescription();
        assertThat(desc).contains("Juntada de mandado cumprido");
        assertThat(desc).contains("RÉU - BANCO BRADESCO S A");
    }

    @Test
    @DisplayName("deve processar alternância de classes infraTrClara e infraTrEscura")
    void shouldProcessBothRowClasses() {
        ParsedData result = parser.parse(loadFixture("eproc/v1.0.0_processo_normal.html"));

        assertThat(result.movements()).hasSize(4);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando tabela não tem linhas de dados")
    void shouldReturnEmptyForNoEvents() {
        ParsedData result = parser.parse(loadFixture("eproc/v1.0.0_sem_movimentacoes.html"));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("deve lançar ParseException quando table.infraTable não existir")
    void shouldThrowWhenTableMissing() {
        String html = "<html><body><p>Sem tabela</p></body></html>";

        assertThatThrownBy(() -> parser.parse(raw(html)))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("Assuntos");
    }

    @Test
    @DisplayName("deve ignorar linhas com menos de 3 células")
    void shouldIgnoreRowsWithFewerThan3Cells() {
        String html = """
                <!DOCTYPE html>
                <html><body>
                <table class="infraTable" summary="Assuntos">
                  <tr><th>Evento</th><th>Data/Hora</th></tr>
                  <tr class="infraTrClara"><td>1</td><td>01/01/2025</td></tr>
                </table>
                </body></html>
                """;

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("deve encontrar tabela mesmo com outras tabelas .infraTable na página")
    void shouldFindCorrectTableBySummaryAttribute() {
        String html = """
                <!DOCTYPE html>
                <html><body>
                <table class="infraTable" summary="OutraTabela">
                  <tr class="infraTrClara"><td>dado</td><td>que nao importa</td><td>ignorar</td></tr>
                </table>
                <table class="infraTable" summary="Assuntos">
                  <tr><th>Evento</th><th>Data/Hora</th><th>Descrição</th><th>Usuário</th><th>Docs</th></tr>
                  <tr class="infraTrClara">
                    <td>1</td><td>01/01/2025 10:00:00</td>
                    <td>Petição inicial recebida</td><td>user</td><td>nenhum</td>
                  </tr>
                </table>
                </body></html>
                """;

        ParsedData result = parser.parse(raw(html));

        assertThat(result.movements()).hasSize(1);
        assertThat(result.movements().get(0).rawDescription())
                .isEqualTo("Petição inicial recebida");
    }

    @Test
    @DisplayName("versão deve ser '1.0.0' e código 'EPROC'")
    void shouldHaveCorrectMetadata() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
        assertThat(parser.getCourtCode()).isEqualTo("EPROC");
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
        return new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.JSOUP);
    }
}