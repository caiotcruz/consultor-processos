package com.consultorprocessos.admin.controller;

import com.consultorprocessos.admin.dto.AdminHealthResponse;
import com.consultorprocessos.admin.service.AdminDlqService;
import com.consultorprocessos.court.repository.CourtRepository;
import com.consultorprocessos.process.repository.ProcessRepository;
import com.consultorprocessos.shared.config.HealthService;
import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/admin/health")
@RequiredArgsConstructor
public class AdminHealthController {

    private final HealthService    healthService;
    private final CourtRepository  courtRepository;
    private final ProcessRepository processRepository;
    private final AdminDlqService  adminDlqService;

    @Value("${app.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminHealthResponse>> detailed() {
        var basicHealth = healthService.check();
        Map<String, String> infraMap = basicHealth.components() != null
                ? basicHealth.components()
                : Map.of();

        List<AdminHealthResponse.CourtHealth> courts = courtRepository.findAll().stream()
                .map(c -> new AdminHealthResponse.CourtHealth(
                        c.getCode(), c.getName(), c.getHealthScore(), c.isActive()))
                .collect(Collectors.toList());

        long pendingProcesses = processRepository.countByStatusIn(
                List.of(com.consultorprocessos.process.entity.ProcessStatus.PENDING,
                        com.consultorprocessos.process.entity.ProcessStatus.OK,
                        com.consultorprocessos.process.entity.ProcessStatus.ERROR));

        var schedulerInfo = new AdminHealthResponse.SchedulerInfo(
                schedulerEnabled, pendingProcesses);

        var queueInfo = new AdminHealthResponse.QueueInfo(
                0L, 0L, adminDlqService.countPending());

        String overallStatus = "UP".equals(basicHealth.status()) ? "UP" : "DEGRADED";

        return ResponseEntity.ok(ApiResponse.success(new AdminHealthResponse(
                overallStatus, Instant.now(), infraMap,
                courts, schedulerInfo, queueInfo)));
    }
}