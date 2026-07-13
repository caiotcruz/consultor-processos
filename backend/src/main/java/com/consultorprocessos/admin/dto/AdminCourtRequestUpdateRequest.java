package com.consultorprocessos.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminCourtRequestUpdateRequest(
        @NotBlank
        @Pattern(regexp = "PENDING|IN_PROGRESS|DONE|REJECTED",
                 message = "Status deve ser PENDING, IN_PROGRESS, DONE ou REJECTED.")
        String status,

        String adminNotes
) {}