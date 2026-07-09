package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.provider.stf.STFProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class STFProviderIT extends BaseProviderIT {

    @Autowired
    private STFProvider stfProvider;

    private static final String PROCESS_NUMBER = "0001234-55.2020.8.26.0001";
    private static final String PROCESS_DIGITS = "00012345520208260001";

    private static final String BUSCA_URL_PATTERN   = "/processos/listarProcessos.asp.*";
    private static final String DETALHE_URL_PATTERN = "/processos/detalhe.asp.*";

    @BeforeEach
    void disableDelays() {
        jdbcTemplate.update(
                "UPDATE courts SET min_delay_ms=0, max_delay_ms=0, rate_limit_per_min=600 " +
                "WHERE code='STF'");
    }

    @Test
    @DisplayName("deve seguir redirect e retornar CrawlerSnapshot válido")
    void shouldFollowRedirectAndReturnSnapshot() {
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .withQueryParam("numeroUnico", equalTo(PROCESS_DIGITS))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                WIRE_MOCK.baseUrl() + "/processos/detalhe.asp?incidente=12345")));

        WIRE_MOCK.stubFor(get(urlPathMatching(DETALHE_URL_PATTERN))
                .withQueryParam("incidente", equalTo("12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBodyFile("stf_normal.html")));

        CrawlerSnapshot snapshot = stfProvider.consultar(PROCESS_NUMBER);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.courtCode()).isEqualTo("STF");
        assertThat(snapshot.processNumber()).isEqualTo(PROCESS_NUMBER);
        assertThat(snapshot.movements()).isNotEmpty();
        assertThat(snapshot.contentHash()).hasSize(64);
        assertThat(snapshot.parserVersion()).isEqualTo("1.0.0");
        assertThat(snapshot.strategyUsed()).isEqualTo(CrawlerStrategy.HTTP);
    }

    @Test
    @DisplayName("deve enviar o número único sem separadores no parâmetro")
    void shouldSendDigitsOnlyAsNumeroUnico() {
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .withQueryParam("numeroUnico", equalTo(PROCESS_DIGITS))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                WIRE_MOCK.baseUrl() + "/processos/detalhe.asp?incidente=12345")));

        WIRE_MOCK.stubFor(get(urlPathMatching(DETALHE_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBodyFile("stf_normal.html")));

        stfProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.verify(getRequestedFor(urlPathMatching(BUSCA_URL_PATTERN))
                .withQueryParam("numeroUnico", equalTo(PROCESS_DIGITS)));
    }

    @Test
    @DisplayName("dois snapshots do mesmo HTML devem ter o mesmo hash (determinismo)")
    void shouldProduceDeterministicHash() {
        stubRedirectAndDetalhe();

        CrawlerSnapshot s1 = stfProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.resetAll();
        stubRedirectAndDetalhe();

        CrawlerSnapshot s2 = stfProvider.consultar(PROCESS_NUMBER);

        assertThat(s1.contentHash()).isEqualTo(s2.contentHash());
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 403 na busca")
    void shouldThrowBlockedFor403OnSearch() {
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .willReturn(forbidden().withBody("Acesso negado.")));

        assertThatThrownBy(() -> stfProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException quando HTML contém CAPTCHA")
    void shouldThrowBlockedForCaptchaHtml() {
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                WIRE_MOCK.baseUrl() + "/processos/detalhe.asp?incidente=99")));

        WIRE_MOCK.stubFor(get(urlPathMatching(DETALHE_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBodyFile("stf_captcha.html")));

        assertThatThrownBy(() -> stfProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class)
                .hasMessageContaining("captcha");
    }

    @Test
    @DisplayName("deve usar Jsoup quando HTTP falha no primeiro redirect")
    void shouldFallbackToJsoupWhenHttpFails() {
        System.out.println("prim");
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .inScenario("http-fallback")
                .whenScenarioStateIs(STARTED)
                .willReturn(serviceUnavailable())
                .willSetStateTo("retry"));

        System.out.println("sec");
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .inScenario("http-fallback")
                .whenScenarioStateIs("retry")
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                WIRE_MOCK.baseUrl() + "/processos/detalhe.asp?incidente=12345")));

        System.out.println("ter");
        WIRE_MOCK.stubFor(get(urlPathMatching(DETALHE_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBodyFile("stf_normal.html")));

        CrawlerSnapshot snapshot = stfProvider.consultar(PROCESS_NUMBER);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.strategyUsed()).isEqualTo(CrawlerStrategy.JSOUP);
    }

    @Test
    @DisplayName("deve lançar CourtUnavailableException quando todas as estratégias falham")
    void shouldThrowWhenAllStrategiesFail() {
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .willReturn(serverError()));

        assertThatThrownBy(() -> stfProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtUnavailableException.class)
                .hasMessageContaining("STF");
    }

    private void stubRedirectAndDetalhe() {
        WIRE_MOCK.stubFor(get(urlPathMatching(BUSCA_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                WIRE_MOCK.baseUrl() + "/processos/detalhe.asp?incidente=12345")));

        WIRE_MOCK.stubFor(get(urlPathMatching(DETALHE_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBodyFile("stf_normal.html")));
    }
}