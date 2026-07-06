package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.model.Movement;
import com.consultorprocessos.crawler.model.ParsedData;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ParsedDataNormalizer {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    public List<Movement> normalize(ParsedData parsedData) {
        return parsedData.movements().stream()
                .map(raw -> {
                    LocalDate date        = parseDate(raw.rawDate());
                    String    description = cleanDescription(raw.rawDescription());

                    if (description.isBlank()) {
                        return null; 
                    }

                    return new Movement(date, description);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return null;

        String cleaned = rawDate.trim();

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }

        log.warn("Não foi possível parsear a data: '{}'. Retornando null.", cleaned);
        return null;
    }

    private String cleanDescription(String rawDescription) {
        if (rawDescription == null) return "";

        String noHtml = Jsoup.parse(rawDescription).text();

        return noHtml.trim().replaceAll("\\s+", " ");
    }
}