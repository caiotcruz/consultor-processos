package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.RawResponseType;
import com.consultorprocessos.crawler.provider.stjrj.STJRJProvider;
import com.consultorprocessos.shared.exception.NotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class STJRJProviderIT extends BaseProviderIT {

    @Autowired
    private STJRJProvider stjrjProvider;

    private static final String PROCESS_NUMBER   = "0143917-50.2015.8.19.0001";
    private static final String CODIGO_INTERNO   = "2015.001.128775-7";

    private static final String BUSCA_PATH       = "/consultaprocessual/api/processos/por-numeracao-unica";
    private static final String MOVIMENTOS_PATH  = "/consultaprocessual/api/processos/por-numero/movimentos";

    @BeforeEach
    void disableDelays() {
        jdbcTemplate.update(
                "UPDATE courts SET min_delay_ms=0, max_delay_ms=0, rate_limit_per_min=600 " +
                "WHERE code='STJRJ'");
    }

    @Test
    @DisplayName("deve executar dois POSTs e retornar CrawlerSnapshot válido")
    void shouldExecuteTwoPostsAndReturnSnapshot() {
        stubBuscaComSucesso();
        stubMovimentosComSucesso();

        CrawlerSnapshot snapshot = stjrjProvider.consultar(PROCESS_NUMBER);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.courtCode()).isEqualTo("STJRJ");
        assertThat(snapshot.processNumber()).isEqualTo(PROCESS_NUMBER);
        assertThat(snapshot.movements()).isNotEmpty();
        assertThat(snapshot.contentHash()).hasSize(64);
        assertThat(snapshot.parserVersion()).isEqualTo("1.0.0");
        assertThat(snapshot.strategyUsed()).isEqualTo(CrawlerStrategy.HTTP);
    }

    @Test
    @DisplayName("deve enviar CNJ no campo codigoCNJ no corpo do primeiro POST")
    void shouldSendCnjInBuscaBody() {
        stubBuscaComSucesso();
        stubMovimentosComSucesso();

        stjrjProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.verify(postRequestedFor(urlPathEqualTo(BUSCA_PATH))
                .withRequestBody(containing("codigoCNJ"))
                .withRequestBody(containing(PROCESS_NUMBER)));
    }

    @Test
    @DisplayName("deve enviar codigoProcesso interno no segundo POST")
    void shouldSendCodigoProcessoInMovimentosBody() {
        stubBuscaComSucesso();
        stubMovimentosComSucesso();

        stjrjProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.verify(postRequestedFor(urlPathEqualTo(MOVIMENTOS_PATH))
                .withRequestBody(containing("codigoProcesso"))
                .withRequestBody(containing(CODIGO_INTERNO)));
    }

    @Test
    @DisplayName("dois snapshots do mesmo JSON devem ter hash idêntico (determinismo)")
    void shouldProduceDeterministicHash() {
        stubBuscaComSucesso();
        stubMovimentosComSucesso();
        CrawlerSnapshot s1 = stjrjProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.resetAll();
        stubBuscaComSucesso();
        stubMovimentosComSucesso();
        CrawlerSnapshot s2 = stjrjProvider.consultar(PROCESS_NUMBER);

        assertThat(s1.contentHash()).isEqualTo(s2.contentHash());
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 403 no Passo 1")
    void shouldThrowBlockedFor403OnBusca() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(BUSCA_PATH))
                .willReturn(forbidden()));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 429 no Passo 1")
    void shouldThrowBlockedFor429OnBusca() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(BUSCA_PATH))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtUnavailableException para HTTP 500 no Passo 1")
    void shouldThrowUnavailableFor500OnBusca() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(BUSCA_PATH))
                .willReturn(serverError()));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtUnavailableException.class);
    }

    @Test
        @DisplayName("deve lançar NotFoundException quando processo não existe em 1º grau")
        void shouldThrowNotFoundWhenNoFirstDegreEntry() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(BUSCA_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("stjrj_busca_sem_primeiro_grau.json")));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(PROCESS_NUMBER);
        }

    @Test
    @DisplayName("deve lançar CourtUnavailableException para HTTP 500 no Passo 2")
    void shouldThrowUnavailableFor500OnMovimentos() {
        stubBuscaComSucesso();

        WIRE_MOCK.stubFor(post(urlPathEqualTo(MOVIMENTOS_PATH))
                .willReturn(serverError()));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtUnavailableException.class);
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 403 no Passo 2")
    void shouldThrowBlockedFor403OnMovimentos() {
        stubBuscaComSucesso();

        WIRE_MOCK.stubFor(post(urlPathEqualTo(MOVIMENTOS_PATH))
                .willReturn(forbidden()));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    private void stubBuscaComSucesso() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(BUSCA_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("stjrj_busca.json")));
        }

    private void stubMovimentosComSucesso() {
        try {
                String jsonContent = new String(
                        getClass().getClassLoader()
                                .getResourceAsStream("fixtures/parsers/stjrj/v1.0.0_processo_normal.json")
                                .readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                );

                WIRE_MOCK.stubFor(post(urlPathEqualTo(MOVIMENTOS_PATH))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(jsonContent)));

        } catch (Exception e) {
                throw new RuntimeException("Falha ao carregar a fixture para o WireMock: stjrj/v1.0.0_processo_normal.json", e);
        }
        }
}