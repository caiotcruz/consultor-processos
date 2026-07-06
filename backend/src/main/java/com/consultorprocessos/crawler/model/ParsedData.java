package com.consultorprocessos.crawler.model;

import java.util.List;

public record ParsedData(
        String          processNumber,
        List<RawMovement> movements
) {}