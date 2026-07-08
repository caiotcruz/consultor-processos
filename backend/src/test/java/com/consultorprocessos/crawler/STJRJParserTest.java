package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.ParseException;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.ParsedData;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.RawResponseType;
import com.consultorprocessos.crawler.provider.stjrj.STJRJParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class STJRJParserTest {

    private final STJRJParser parser = new STJRJParser(new ObjectMapper());

    @Test
    @DisplayName("deve parsear JSON normal com 4 movimentos")
    void shouldParseNormalJson() {
        ParsedData result = parser.parse(loadFixture("stjrj/v1.0.0_processo_normal.json"));

        assertThat(result.movements()).hasSize(4);
    }

    @Test
    @DisplayName("deve extrair data corretamente do campo dtMovimento")
    void shouldExtractDateFromDtMovimento() {
        ParsedData result = parser.parse(loadFixture("stjrj/v1.0.0_processo_normal.json"));

        assertThat(result.movements().get(0).rawDate()).isEqualTo("26/06/2026");
        assertThat(result.movements().get(1).rawDate()).isEqualTo("23/06/2026");
    }

    @Test
    @DisplayName("deve concatenar descrMov e descricao quando ambos presentes")
    void shouldConcatenateDescrMovAndDescricao() {
        ParsedData result = parser.parse(loadFixture("stjrj/v1.0.0_processo_normal.json"));

        String desc = result.movements().get(0).rawDescription();
        assertThat(desc).contains("Juntada - Petição");
        assertThat(desc).contains("Documento eletrônico juntado");
        assertThat(desc).contains(" — ");
    }

    @Test
    @DisplayName("deve usar apenas descrMov quando descricao é null")
    void shouldUseOnlyDescrMovWhenDescricaoIsNull() {
        ParsedData result = parser.parse(loadFixture("stjrj/v1.0.0_processo_normal.json"));

        String desc = result.movements().get(3).rawDescription();
        assertThat(desc).isEqualTo("Decurso de Prazo");
        assertThat(desc).doesNotContain(" — ");
    }

    @Test
    @DisplayName("deve processar movimentos com descricao longa (texto jurídico)")
    void shouldProcessLongDescricao() {
        ParsedData result = parser.parse(loadFixture("stjrj/v1.0.0_processo_normal.json"));

        String desc = result.movements().get(1).rawDescription();
        assertThat(desc).contains("Conclusão ao Juiz");
        assertThat(desc).contains("sistema eletrônico");
    }

    @Test
    @DisplayName("deve retornar lista vazia para movimentosProc vazio")
    void shouldReturnEmptyForEmptyMovimentos() {
        ParsedData result = parser.parse(
                loadFixture("stjrj/v1.0.0_sem_movimentacoes.json"));

        assertThat(result.movements()).isEmpty();
    }

    @Test
    @DisplayName("deve lançar ParseException quando movimentosProc não existir")
    void shouldThrowWhenMovimentosProcMissing() {
        String json = "{\"outroCampo\": []}";

        assertThatThrownBy(() -> parser.parse(rawJson(json)))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("movimentosProc");
    }

    @Test
    @DisplayName("deve lançar ParseException para JSON inválido")
    void shouldThrowForInvalidJson() {
        assertThatThrownBy(() -> parser.parse(rawJson("isso nao e json")))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("TJRJ");
    }

    @Test
    @DisplayName("deve lançar ParseException quando movimentosProc não é array")
    void shouldThrowWhenMovimentosProcIsNotArray() {
        String json = "{\"movimentosProc\": \"string inválida\"}";

        assertThatThrownBy(() -> parser.parse(rawJson(json)))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("movimentosProc");
    }

    @Test
    @DisplayName("deve ignorar movimentos sem descrMov e descricao")
    void shouldIgnoreMovimentsWithNoDescription() {
        String json = """
                {
                  "movimentosProc": [
                    {"dtMovimento": "01/01/2025", "descrMov": "", "descricao": null},
                    {"dtMovimento": "02/01/2025", "descrMov": "Válido", "descricao": null}
                  ]
                }
                """;

        ParsedData result = parser.parse(rawJson(json));

        assertThat(result.movements()).hasSize(1);
        assertThat(result.movements().get(0).rawDescription()).isEqualTo("Válido");
    }

    @Test
    @DisplayName("versão deve ser '1.0.0' e código 'STJRJ'")
    void shouldHaveCorrectMetadata() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
        assertThat(parser.getCourtCode()).isEqualTo("STJRJ");
    }

    private RawResponse loadFixture(String path) {
        try {
            String json = new String(
                    getClass().getClassLoader()
                            .getResourceAsStream("fixtures/parsers/" + path)
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
            return rawJson(json);
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Fixture não encontrada: fixtures/parsers/" + path, e);
        }
    }

    private RawResponse rawJson(String json) {
        return new RawResponse(json, 200, RawResponseType.JSON, CrawlerStrategy.HTTP);
    }
}