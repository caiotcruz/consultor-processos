package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.provider.stf.STFProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class STFProviderIT extends BaseProviderIT {

    @Autowired
    private STFProvider stfProvider;

    private static final String PROCESS_NUMBER = "0001234-55.2020.8.26.0001";
    private static final String MOCK_URL_PATTERN =
            "/processos/detalhe.asp.*";

    @BeforeEach
    void configureDelays() {
        jdbcTemplate.update(
            "UPDATE courts SET min_delay_ms=0, max_delay_ms=0, rate_limit_per_min=60 " +
            "WHERE code='STF'");
    }

    @Test
    @DisplayName("deve retornar CrawlerSnapshot válido para HTML normal")
    void shouldReturnValidSnapshotForNormalHtml() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/html; charset=UTF-8")
                                .withBodyFile("stf_normal.html")
                ));

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
    @DisplayName("deve lançar CourtBlockedException para resposta 403")
    void shouldThrowBlockedExceptionFor403() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(forbidden()
                        .withBody("Acesso negado.")));

        assertThatThrownBy(() -> stfProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException quando HTML contém CAPTCHA")
    void shouldThrowBlockedExceptionForCaptchaHtml() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/html")
                                .withBodyFile("stf_captcha.html")
                ));

        assertThatThrownBy(() -> stfProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class)
                .hasMessageContaining("captcha");
    }

    @Test
    @DisplayName("deve usar JsoupCrawler como fallback quando HTTP falhar")
    void shouldFallbackToJsoupWhenHttpFails() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .inScenario("http-fallback")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(serviceUnavailable())
                .willSetStateTo("retry"));

        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
        .inScenario("http-fallback")
        .whenScenarioStateIs("retry")
        .willReturn(
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBodyFile("stf_normal.html")
        ));

        CrawlerSnapshot snapshot = stfProvider.consultar(PROCESS_NUMBER);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.strategyUsed()).isEqualTo(CrawlerStrategy.JSOUP);
        assertThat(snapshot.movements()).isNotEmpty();
    }

    @Test
    @DisplayName("deve lançar CourtUnavailableException quando todas as estratégias falharem")
    void shouldThrowWhenAllStrategiesFail() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(serverError()
                        .withBody("Internal Server Error")));

        assertThatThrownBy(() -> stfProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtUnavailableException.class)
                .hasMessageContaining("STF");
    }

    @Test
    @DisplayName("dois snapshots do mesmo HTML devem ter o mesmo hash")
    void shouldProduceSameHashForSameHtml() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/html; charset=UTF-8")
                                .withBodyFile("stf_normal.html")
                ));

        CrawlerSnapshot s1 = stfProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.resetScenarios();
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/html; charset=UTF-8")
                                .withBodyFile("stf_normal.html")
                ));

        CrawlerSnapshot s2 = stfProvider.consultar(PROCESS_NUMBER);

        assertThat(s1.contentHash()).isEqualTo(s2.contentHash());
    }
}