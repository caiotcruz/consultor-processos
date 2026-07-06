package com.consultorprocessos.mock.controller;

import com.consultorprocessos.mock.service.MockTribunalStateService;
import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/control")
@Profile("dev")
@RequiredArgsConstructor
public class ControlController {

    private final MockTribunalStateService stateService;

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
}