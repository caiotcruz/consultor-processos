package com.consultorprocessos.admin.dto;

import jakarta.validation.constraints.Pattern;

public record AdminUserUpdateRequest(
        @Pattern(regexp = "ACTIVE|SUSPENDED",
                 message = "Status deve ser ACTIVE ou SUSPENDED.")
        String status,

        String plan
) {}