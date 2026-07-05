package com.consultorprocessos.court.dto;

import java.util.UUID;

public record CourtResponse(
        UUID    id,
        String  name,
        String  code,
        boolean active,
        int     healthScore
) {}