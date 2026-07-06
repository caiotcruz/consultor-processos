package com.consultorprocessos.shared;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.RawResponse;
import com.consultorprocessos.crawler.model.RawResponseType;
import com.consultorprocessos.crawler.service.BlockDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class BlockDetectorTest {

    private final BlockDetector detector = new BlockDetector();

    @Test
    @DisplayName("deve lançar exceção para HTTP 403")
    void shouldThrowFor403() {
        RawResponse response = response("<html>Forbidden</html>", 403);
        assertThatThrownBy(() -> detector.check(response))
                .isInstanceOf(CourtBlockedException.class)
                .hasMessageContaining("403");
    }

    @Test
    @DisplayName("deve lançar exceção para HTTP 429")
    void shouldThrowFor429() {
        RawResponse response = response("<html>Too Many Requests</html>", 429);
        assertThatThrownBy(() -> detector.check(response))
                .isInstanceOf(CourtBlockedException.class)
                .hasMessageContaining("429");
    }

    @ParameterizedTest
    @ValueSource(strings = {"captcha", "CAPTCHA", "recaptcha", "acesso negado",
                            "acesso bloqueado", "too many requests", "rate limit"})
    @DisplayName("deve lançar exceção quando HTML contém sinal de bloqueio")
    void shouldThrowForBlockSignalInContent(String signal) {
        RawResponse response = response(
                "<html><body>Erro: " + signal + " detectado.</body></html>", 200);
        assertThatThrownBy(() -> detector.check(response))
                .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("não deve lançar exceção para resposta normal")
    void shouldNotThrowForNormalResponse() {
        RawResponse response = response(
                "<html><body>Processo encontrado.</body></html>", 200);
        assertThatNoException().isThrownBy(() -> detector.check(response));
    }

    @Test
    @DisplayName("não deve lançar exceção para HTTP 200 com conteúdo limpo")
    void shouldNotThrowFor200WithCleanContent() {
        RawResponse response = response(
                "<html><body>Movimentações do processo</body></html>", 200);
        assertThatNoException().isThrownBy(() -> detector.check(response));
    }

    private RawResponse response(String content, int status) {
        return new RawResponse(content, status, RawResponseType.HTML, CrawlerStrategy.HTTP);
    }
}