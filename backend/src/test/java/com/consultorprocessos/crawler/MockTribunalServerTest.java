package com.consultorprocessos.crawler;

import com.consultorprocessos.shared.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev"})
@Tag("integration")
class MockTribunalServerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        restTemplate.postForEntity(baseUrl + "/control/reset", null, String.class);
    }

    @Test
    @DisplayName("deve retornar HTML com movimentos padrão para processo válido")
    void shouldReturnDefaultHtml() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/mock/STF/0001234-55.2020.8.26.0001", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .satisfies(ct -> assertThat(ct.toString()).contains("text/html"));
        assertThat(response.getBody())
                .contains("movement-description")
                .contains("Petição inicial distribuída.");
    }

    @Test
    @DisplayName("deve refletir o processNumber no HTML retornado")
    void shouldReflectProcessNumberInResponse() {
        String processNumber = "0009999-55.2025.8.26.0001";
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/mock/STF/" + processNumber, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(processNumber);
    }

    @Test
    @DisplayName("deve incluir nova movimentação após inject-change")
    void shouldIncludeNewMovementAfterInjectChange() {
        String processNumber = "0001234-55.2020.8.26.0001";

        String html1 = restTemplate.getForObject(
                baseUrl + "/mock/STF/" + processNumber, String.class);
        assertThat(html1).doesNotContain("Julgamento finalizado.");

        restTemplate.postForEntity(
                baseUrl + "/control/inject-change",
                Map.of(
                        "court", "STF",
                        "processNumber", processNumber,
                        "description", "Julgamento finalizado.",
                        "date", "2025-06-01"
                ),
                String.class
        );

        String html2 = restTemplate.getForObject(
                baseUrl + "/mock/STF/" + processNumber, String.class);
        assertThat(html2).contains("Julgamento finalizado.");
    }

    @Test
    @DisplayName("deve retornar 408 após inject-timeout")
    void shouldReturn408AfterInjectTimeout() {
        restTemplate.postForEntity(
                baseUrl + "/control/inject-timeout",
                Map.of("court", "STF", "count", 1),
                String.class
        );

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/mock/STF/0001234-55.2020.8.26.0001", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);

        ResponseEntity<String> normal = restTemplate.getForEntity(
                baseUrl + "/mock/STF/0001234-55.2020.8.26.0001", String.class);
        assertThat(normal.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("deve retornar 403 após inject-block")
    void shouldReturn403AfterInjectBlock() {
        restTemplate.postForEntity(
                baseUrl + "/control/inject-block",
                Map.of("court", "EPROC", "count", 1),
                String.class
        );

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/mock/EPROC/5001234-88.2021.4.02.5001", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("bloqueado");
    }

    @Test
    @DisplayName("deve retornar HTML com CAPTCHA após inject-captcha")
    void shouldReturnCaptchaHtmlAfterInjectCaptcha() {
        restTemplate.postForEntity(
                baseUrl + "/control/inject-captcha",
                Map.of("court", "STF"),
                String.class
        );

        String html = restTemplate.getForObject(
                baseUrl + "/mock/STF/0001234-55.2020.8.26.0001", String.class);

        assertThat(html).containsIgnoringCase("captcha");
    }

    @Test
    @DisplayName("reset deve limpar todos os comportamentos injetados")
    void shouldClearAllInjectedBehaviorsOnReset() {
        restTemplate.postForEntity(
                baseUrl + "/control/inject-timeout",
                Map.of("court", "STF", "count", 5),
                String.class
        );

        restTemplate.postForEntity(baseUrl + "/control/reset", null, String.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/mock/STF/0001234-55.2020.8.26.0001", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /control/state deve retornar estado atual do mock")
    void shouldReturnCurrentState() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/control/state", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("pendingTimeouts");
    }
}