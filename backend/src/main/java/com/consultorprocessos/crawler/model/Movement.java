package com.consultorprocessos.crawler.model;

import java.time.LocalDate;

public record Movement(
        LocalDate date,
        String    description
) {}