package com.consultorprocessos.process.controller;

import com.consultorprocessos.auth.controller.AuthController.MessageResponse;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.process.dto.*;
import com.consultorprocessos.process.entity.ProcessStatus;
import com.consultorprocessos.process.service.ProcessService;
import com.consultorprocessos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/processes")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> subscribe(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody @Valid CreateProcessRequest request) {

        SubscriptionResult result = processService.subscribe(
            principal,
            request.processNumber(),
            request.courtCode(),
            request.alias()
        );

        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(
                        processService.getById(principal, result.subscription().getId())));
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(new CourtUnavailableResponse(
                    result.courtRequestId(),
                    "O tribunal informado ainda não está disponível. " +
                    "Registramos sua solicitação e nossa equipe iniciará " +
                    "a implementação em até 7 dias.",
                    7
                )));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProcessSummaryResponse>>> list(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) ProcessStatus status,
            @PageableDefault(size = 20, sort = "createdAt",
                            direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<ProcessSummaryResponse> page =
                processService.listByUser(principal, active, status, pageable);

        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcessSubscriptionResponse>> getById(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(
            ApiResponse.success(processService.getById(principal, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcessSubscriptionResponse>> updateAlias(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id,
            @RequestBody @Valid UpdateAliasRequest request) {

        return ResponseEntity.ok(
            ApiResponse.success(
                processService.updateAlias(principal, id, request.alias())));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<ProcessSubscriptionResponse>> deactivate(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(
            ApiResponse.success(processService.deactivate(principal, id)));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<ProcessSubscriptionResponse>> reactivate(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(
            ApiResponse.success(processService.reactivate(principal, id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<MessageResponse>> delete(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id) {

        processService.delete(principal, id);
        return ResponseEntity.ok(ApiResponse.success(
            new MessageResponse("Processo removido. Uma vaga foi liberada no seu plano.")));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<ProcessHistoryResponse>>> getHistory(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "detectedAt",
                            direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success(processService.getHistory(principal, id, pageable)));
    }

    public record CourtUnavailableResponse(
            UUID   courtRequestId,
            String message,
            int    estimatedDays
    ) {}

    public record UpdateAliasRequest(
            @Size(max = 200, message = "Alias não pode ter mais de 200 caracteres.")
            String alias
    ) {}
}