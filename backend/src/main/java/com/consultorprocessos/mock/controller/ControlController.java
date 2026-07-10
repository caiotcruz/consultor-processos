package com.consultorprocessos.mock.controller;

import com.consultorprocessos.court.repository.CourtRepository;
import com.consultorprocessos.mock.service.MockTribunalStateService;
import com.consultorprocessos.process.repository.ProcessRepository;
import com.consultorprocessos.scheduler.model.CrawlRequestMessage;
import com.consultorprocessos.shared.config.RabbitConfig;
import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/control")
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class ControlController {

    private final MockTribunalStateService stateService;
    private final ProcessRepository      processRepository;
    private final CourtRepository        courtRepository;
    private final RabbitTemplate         rabbitTemplate;

    @PostMapping("/inject-change")
    public ResponseEntity<ApiResponse<String>> injectChange(
            @RequestBody Map<String, String> body) {

        stateService.injectChange(
                body.getOrDefault("court", "STF"),
                body.getOrDefault("processNumber", "*"),
                body.getOrDefault("description", "Nova movimentação de teste."),
                body.getOrDefault("date", "2025-06-01")
        );
        return ResponseEntity.ok(ApiResponse.success("Mudança injetada."));
    }

    @PostMapping("/inject-timeout")
    public ResponseEntity<ApiResponse<String>> injectTimeout(
            @RequestBody Map<String, Object> body) {

        String court = (String) body.getOrDefault("court", "STF");
        int    count = Integer.parseInt(body.getOrDefault("count", "1").toString());
        stateService.injectTimeout(court, count);
        return ResponseEntity.ok(ApiResponse.success("Timeout injetado: " + count + " requisição(ões)."));
    }

    @PostMapping("/inject-block")
    public ResponseEntity<ApiResponse<String>> injectBlock(
            @RequestBody Map<String, Object> body) {

        String court = (String) body.getOrDefault("court", "STF");
        int    count = Integer.parseInt(body.getOrDefault("count", "1").toString());
        stateService.injectBlock(court, count);
        return ResponseEntity.ok(ApiResponse.success("Bloqueio injetado: " + count + " requisição(ões)."));
    }

    @PostMapping("/inject-captcha")
    public ResponseEntity<ApiResponse<String>> injectCaptcha(
            @RequestBody Map<String, String> body) {

        stateService.injectCaptcha(body.getOrDefault("court", "STF"));
        return ResponseEntity.ok(ApiResponse.success("CAPTCHA injetado."));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<String>> reset() {
        stateService.reset();
        return ResponseEntity.ok(ApiResponse.success("Estado do Mock Tribunal resetado."));
    }

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getState() {
        return ResponseEntity.ok(ApiResponse.success(stateService.getState()));
    }

    @Transactional(readOnly = true)
    @PostMapping("/recrawl")
    public ResponseEntity<ApiResponse<String>> forceRecrawl(
            @RequestBody Map<String, Object> body) {

        boolean all = Boolean.TRUE.equals(body.get("all"));

        if (all) {
            List<com.consultorprocessos.process.entity.Process> processes =
                    processRepository.findAll().stream()
                            .filter(p -> p.getCourt().isActive())
                            .filter(p -> p.getStatus().name().matches("PENDING|OK|ERROR"))
                            .toList();

            processes.forEach(this::publishRecrawl);

            log.info("[CONTROL] Recrawl forçado: {} processos publicados na fila.", processes.size());
            return ResponseEntity.ok(ApiResponse.success(
                    processes.size() + " processo(s) publicado(s) na fila de crawl."));
        }

        String processNumber = (String) body.get("processNumber");
        if (processNumber == null || processNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR",
                            "Informe 'processNumber' ou { \"all\": true }."));
        }

        List<com.consultorprocessos.process.entity.Process> matches =
                processRepository.findAll().stream()
                        .filter(p -> p.getProcessNumber().equals(processNumber))
                        .toList();

        if (matches.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("NOT_FOUND",
                            "Processo não encontrado: " + processNumber));
        }

        matches.forEach(this::publishRecrawl);
        log.info("[CONTROL] Recrawl forçado: processo={}", processNumber);

        return ResponseEntity.ok(ApiResponse.success(
                "Processo " + processNumber + " publicado na fila de crawl."));
    }

    private void publishRecrawl(com.consultorprocessos.process.entity.Process process) {
        CrawlRequestMessage message = new CrawlRequestMessage(
                process.getId(),
                process.getCourt().getId(),
                process.getProcessNumber(),
                process.getCourt().getCode(),
                0
        );
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL,
                RabbitConfig.ROUTING_KEY_CRAWL_REQUEST,
                message
        );
        log.debug("[CONTROL] Publicado na fila: processo={} tribunal={}",
                process.getProcessNumber(), process.getCourt().getCode());
    }
}