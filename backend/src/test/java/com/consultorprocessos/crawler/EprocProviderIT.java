package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.provider.eproc.EprocProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class EprocProviderIT extends BaseProviderIT {

    @Autowired
    private EprocProvider eprocProvider;

    private static final String PROCESS_NUMBER     = "3101580-09.2026.8.19.0001";
    private static final String FORM_URL_PATTERN   = "/eproc/externo_controlador.php";
    private static final String REDIRECT_URL       =
            "/eproc/externo_controlador.php?acao=processo_seleciona_publica" +
            "&num_processo=31015800920268190001";

    @BeforeEach
    void disableDelays() {
        jdbcTemplate.update(
                "UPDATE courts SET min_delay_ms=0, max_delay_ms=0, rate_limit_per_min=600 " +
                "WHERE code='EPROC'");
    }

    @Test
    @DisplayName("deve fazer POST no formulário e seguir redirect para retornar snapshot válido")
    void shouldPostFormFollowRedirectAndReturnSnapshot() {
        stubFormPostWithRedirect();

        CrawlerSnapshot snapshot = eprocProvider.consultar(PROCESS_NUMBER);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.courtCode()).isEqualTo("EPROC");
        assertThat(snapshot.processNumber()).isEqualTo(PROCESS_NUMBER);
        assertThat(snapshot.movements()).isNotEmpty();
        assertThat(snapshot.contentHash()).hasSize(64);
        assertThat(snapshot.parserVersion()).isEqualTo("1.0.0");
        assertThat(snapshot.strategyUsed()).isEqualTo(CrawlerStrategy.JSOUP);
    }

    @Test
    @DisplayName("deve enviar txtNumProcesso com o formato CNJ (com separadores)")
    void shouldSendProcessNumberWithCnjFormat() {
        stubFormPostWithRedirect();

        eprocProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.verify(postRequestedFor(urlPathEqualTo(FORM_URL_PATTERN))
                .withRequestBody(containing("txtNumProcesso=3101580-09.2026.8.19.0001")));
    }

    @Test
    @DisplayName("dois snapshots do mesmo HTML devem ter hash idêntico (determinismo)")
    void shouldProduceDeterministicHash() {
        stubFormPostWithRedirect();
        CrawlerSnapshot s1 = eprocProvider.consultar(PROCESS_NUMBER);

        WIRE_MOCK.resetAll();
        stubFormPostWithRedirect();
        CrawlerSnapshot s2 = eprocProvider.consultar(PROCESS_NUMBER);

        assertThat(s1.contentHash()).isEqualTo(s2.contentHash());
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 403 no POST")
    void shouldThrowBlockedFor403OnPost() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(FORM_URL_PATTERN))
                .willReturn(forbidden().withBody("Acesso negado.")));

        assertThatThrownBy(() -> eprocProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 429")
    void shouldThrowBlockedFor429() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(FORM_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withBody("Too Many Requests")));

        assertThatThrownBy(() -> eprocProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtUnavailableException quando portal retorna 500")
    void shouldThrowWhenPortalUnavailable() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(FORM_URL_PATTERN))
                .willReturn(serverError()));

        assertThatThrownBy(() -> eprocProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtUnavailableException.class)
                .hasMessageContaining("EPROC");
    }

    private void stubFormPostWithRedirect() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo(FORM_URL_PATTERN))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", WIRE_MOCK.baseUrl() + REDIRECT_URL)));

        WIRE_MOCK.stubFor(get(urlPathEqualTo(FORM_URL_PATTERN))
                .withQueryParam("acao", equalTo("processo_seleciona_publica"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBodyFile("eproc_normal.html")));
        }
}