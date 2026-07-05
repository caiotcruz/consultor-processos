package com.consultorprocessos.court.controller;

import com.consultorprocessos.court.dto.CourtResponse;
import com.consultorprocessos.court.service.CourtService;
import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/courts")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourtResponse>>> listActive() {
        return ResponseEntity.ok(ApiResponse.success(courtService.listActive()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<CourtResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(courtService.getByCode(code)));
    }
}