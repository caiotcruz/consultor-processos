package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.eproc.EprocProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class EprocProviderIT extends BaseProviderIT {

    @Autowired
    private EprocProvider eprocProvider;

    private static final String PROCESS_NUMBER  = "5001234-88.2021.4.02.5001";
    private static final String MOCK_URL_PATTERN = "/epaprocesso.*";

    @BeforeEach
    void configureDelays() {
        jdbcTemplate.update(
            "UPDATE courts SET min_delay_ms=0, max_delay_ms=0, rate_limit_per_min=60 " +
            "WHERE code='EPROC'");
    }

    @Test
    @DisplayName("deve retornar CrawlerSnapshot válido para HTML normal do eProc")
    void shouldReturnValidSnapshotForNormalHtml() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/html; charset=UTF-8")
                                .withBodyFile("eproc_normal.html")
                ));

        CrawlerSnapshot snapshot = eprocProvider.consultar(PROCESS_NUMBER);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.courtCode()).isEqualTo("EPROC");
        assertThat(snapshot.movements()).isNotEmpty();
        assertThat(snapshot.contentHash()).hasSize(64);
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para HTTP 403")
    void shouldThrowForForbidden() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(forbidden()));

        assertThatThrownBy(() -> eprocProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve lançar CourtUnavailableException quando portal indisponível")
    void shouldThrowWhenPortalUnavailable() {
        WIRE_MOCK.stubFor(get(urlPathMatching(MOCK_URL_PATTERN))
                .willReturn(serverError()));

        assertThatThrownBy(() -> eprocProvider.consultar(PROCESS_NUMBER))
                .isInstanceOf(CourtUnavailableException.class);
    }
}