package com.consultorprocessos.notification.controller;

import com.consultorprocessos.auth.controller.AuthController.MessageResponse;
import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.notification.dto.RegisterDeviceRequest;
import com.consultorprocessos.notification.entity.UserDeviceToken;
import com.consultorprocessos.notification.repository.UserDeviceTokenRepository;
import com.consultorprocessos.auth.repository.UserRepository;
import com.consultorprocessos.shared.exception.NotFoundException;
import com.consultorprocessos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users/me/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private static final int MAX_DEVICES_PER_USER = 10;

    private final UserDeviceTokenRepository deviceRepository;
    private final UserRepository            userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> register(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody @Valid RegisterDeviceRequest request) {

        deviceRepository.findByToken(request.token()).ifPresentOrElse(
                existing -> {
                    if (existing.getUser().getId().equals(principal.getUserId())) {
                        existing.markUsed();
                        deviceRepository.save(existing);
                        log.debug("Token FCM já registrado. last_used_at atualizado: userId={}",
                                principal.getUserId());
                    }
                },
                () -> {
                    int count = deviceRepository.countByUserId(principal.getUserId());
                    if (count >= MAX_DEVICES_PER_USER) {
                        throw new com.consultorprocessos.shared.exception
                                .DomainException("DEVICE_LIMIT_REACHED",
                                "Limite de " + MAX_DEVICES_PER_USER + " dispositivos atingido.",
                                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY) {};
                    }

                    User user = userRepository.getReferenceById(principal.getUserId());
                    UserDeviceToken device = new UserDeviceToken();
                    device.setUser(user);
                    device.setToken(request.token());
                    device.setPlatform(request.platform().toUpperCase());
                    deviceRepository.save(device);

                    log.info("Dispositivo registrado: userId={} platform={}",
                            principal.getUserId(), request.platform());
                }
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new MessageResponse("Dispositivo registrado.")));
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<ApiResponse<MessageResponse>> unregister(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable String token) {

        deviceRepository.findByToken(token)
                .filter(d -> d.getUser().getId().equals(principal.getUserId()))
                .ifPresent(d -> deviceRepository.delete(d));

        return ResponseEntity.ok(ApiResponse.success(
                new MessageResponse("Dispositivo removido.")));
    }
}