package com.consultorprocessos.mock.controller;

import com.consultorprocessos.mock.service.MockTribunalStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class MockCourtController {

    private final MockTribunalStateService stateService;

    @GetMapping(value = "/mock/{court}/{processNumber}",
                produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getMockResponse(
            @PathVariable String court,
            @PathVariable String processNumber) {

        log.debug("[MOCK] Requisição: GET /mock/{}/{}", court, processNumber);

        if (stateService.shouldTimeout(court)) {
            log.info("[MOCK] Simulando timeout para tribunal {}.", court);
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .body("<html><body>Request Timeout</body></html>");
        }

        if (stateService.shouldBlock(court)) {
            log.info("[MOCK] Simulando bloqueio para tribunal {}.", court);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("<html><body>Acesso bloqueado</body></html>");
        }

        if (stateService.shouldCaptcha(court)) {
            log.info("[MOCK] Simulando CAPTCHA para tribunal {}.", court);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body>Por favor resolva o CAPTCHA para continuar.</body></html>");
        }

        String html = stateService.buildHtml(court, processNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}