// src/main/java/com/consultorprocessos/crawler/service/HashGenerator.java
package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.Movement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class HashGenerator {

    private final ObjectMapper objectMapper;

    public String generate(CrawlerSnapshot snapshot) {
        String canonical = buildCanonicalJson(snapshot);
        return sha256(canonical);
    }

    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalize(input).getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private String buildCanonicalJson(CrawlerSnapshot snapshot) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("processNumber", snapshot.processNumber());
            root.put("courtCode",     snapshot.courtCode());

            ArrayNode movements = root.putArray("movements");
            snapshot.movements().stream()
                    .sorted(Comparator
                            .comparing((Movement m) -> m.date() != null ? m.date().toString() : "")
                            .thenComparing(Movement::description))
                    .forEach(m -> {
                        ObjectNode mov = movements.addObject();
                        mov.put("date",        m.date() != null ? m.date().toString() : "");
                        mov.put("description", normalize(m.description()));
                    });

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar snapshot para hash", e);
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}