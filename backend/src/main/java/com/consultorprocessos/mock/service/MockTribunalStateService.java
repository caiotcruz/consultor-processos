package com.consultorprocessos.mock.service;

import com.consultorprocessos.mock.model.InjectedMovement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Profile("dev")
@Slf4j
public class MockTribunalStateService {

    private final Map<String, List<InjectedMovement>> injectedMovements =
            new ConcurrentHashMap<>();

    private final Map<String, AtomicInteger> pendingTimeouts = new ConcurrentHashMap<>();

    private final Map<String, AtomicInteger> pendingBlocks = new ConcurrentHashMap<>();

    private final Set<String> pendingCaptchas =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void injectChange(String court, String processNumber,
                             String description, String date) {
        String key = key(court, processNumber);
        injectedMovements.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new InjectedMovement(date, description));
        log.info("[MOCK] Mudança injetada: {} | {} | {}", court, processNumber, description);
    }

    public void injectTimeout(String court, int count) {
        pendingTimeouts.put(court.toUpperCase(), new AtomicInteger(count));
        log.info("[MOCK] Timeout injetado: {} por {} requisição(ões)", court, count);
    }

    public void injectBlock(String court, int count) {
        pendingBlocks.put(court.toUpperCase(), new AtomicInteger(count));
        log.info("[MOCK] Bloqueio injetado: {} por {} requisição(ões)", court, count);
    }

    public void injectCaptcha(String court) {
        pendingCaptchas.add(court.toUpperCase());
        log.info("[MOCK] CAPTCHA injetado: {}", court);
    }

    public void reset() {
        injectedMovements.clear();
        pendingTimeouts.clear();
        pendingBlocks.clear();
        pendingCaptchas.clear();
        log.info("[MOCK] Estado resetado.");
    }

    public boolean shouldTimeout(String court) {
        AtomicInteger counter = pendingTimeouts.get(court.toUpperCase());
        if (counter == null || counter.get() <= 0) return false;
        return counter.decrementAndGet() >= 0;
    }

    public boolean shouldBlock(String court) {
        AtomicInteger counter = pendingBlocks.get(court.toUpperCase());
        if (counter == null || counter.get() <= 0) return false;
        return counter.decrementAndGet() >= 0;
    }

    public boolean shouldCaptcha(String court) {
        return pendingCaptchas.remove(court.toUpperCase());
    }

    public String buildHtml(String court, String processNumber) {
        List<InjectedMovement> extra = injectedMovements.getOrDefault(
                key(court, processNumber), List.of());

        List<InjectedMovement> allMovements = new ArrayList<>();
        allMovements.add(new InjectedMovement("2025-01-10", "Petição inicial distribuída."));
        allMovements.add(new InjectedMovement("2025-02-20", "Conclusos ao relator."));
        allMovements.addAll(extra);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><title>Mock Tribunal - ")
          .append(court.toUpperCase())
          .append("</title></head><body>");
        sb.append("<div id=\"process-number\">").append(processNumber).append("</div>");
        sb.append("<div id=\"movements\">");

        for (InjectedMovement m : allMovements) {
            sb.append("<div class=\"movement\">")
              .append("<span class=\"movement-date\">").append(m.date()).append("</span>")
              .append("<span class=\"movement-description\">")
              .append(m.description())
              .append("</span>")
              .append("</div>");
        }

        sb.append("</div></body></html>");
        return sb.toString();
    }

    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("pendingTimeouts",  pendingTimeouts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().get())));
        state.put("pendingBlocks",    pendingBlocks.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().get())));
        state.put("pendingCaptchas",  pendingCaptchas);
        state.put("injectedChanges",  injectedMovements.keySet());
        return state;
    }

    private String key(String court, String processNumber) {
        return court.toUpperCase() + ":" + processNumber;
    }
}