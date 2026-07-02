package com.consultorprocessos.shared.config;

import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthService.check();

        int status = "UP".equals(response.status()) ? 200 : 503;
        return ResponseEntity.status(status).body(response);
    }

    public record HealthResponse(
            String              status,
            Instant             timestamp,
            Map<String, String> components
    ) {}
}