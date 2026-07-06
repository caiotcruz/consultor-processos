// src/test/java/com/consultorprocessos/crawler/STJRJProviderIT.java
package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.stjrj.STJRJProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class STJRJProviderIT extends BaseProviderIT {

    @Autowired
    private STJRJProvider stjrjProvider;

    private static final String PROCESS_NUMBER  = "0002345-11.2022.8.19.0001";
    private static final String MOCK_URL_PATTERN = "/consultaProcessual.*";

    @BeforeEach
    void configureDelays() {
        jdbcTemplate.update(
            "UPDATE courts SET min_delay_ms=0, max_delay_ms=0, rate_limit_per_min=60 " +
            "WHERE code='STJRJ'");
    }

    @Test
    @DisplayName("deve retornar CrawlerSnapshot válido para HTML normal do TJRJ")
    void shouldReturnValidSnapshotForNormalHtml() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/html; charset=UTF-8")
                                .withBodyFile("stjrj_normal.html")
                ));

        CrawlerSnapshot snapshot = stjrjProvider.consultar(PROCESS_NUMBER);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.courtCode()).isEqualTo("STJRJ");
        assertThat(snapshot.movements()).isNotEmpty();
        assertThat(snapshot.contentHash()).hasSize(64);
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 429")
    void shouldThrowForRateLimit() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(aResponse().withStatus(429)
                        .withBody("Too Many Requests")));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtUnavailableException quando portal indisponível")
    void shouldThrowWhenPortalUnavailable() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(serverError()));

        assertThatThrownBy(() -> stjrjProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtUnavailableException.class);
    }
}