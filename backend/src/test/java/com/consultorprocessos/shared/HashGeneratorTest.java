package com.consultorprocessos.shared;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.Movement;
import com.consultorprocessos.crawler.service.HashGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class HashGeneratorTest {

    private HashGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HashGenerator(new ObjectMapper());
    }

    @Test
    @DisplayName("mesmo conteúdo deve gerar o mesmo hash")
    void shouldGenerateSameHashForSameContent() {
        CrawlerSnapshot s1 = buildSnapshot(List.of(
                new Movement(LocalDate.of(2025, 3, 15), "Conclusos para julgamento.")));
        CrawlerSnapshot s2 = buildSnapshot(List.of(
                new Movement(LocalDate.of(2025, 3, 15), "Conclusos para julgamento.")));

        assertThat(generator.generate(s1)).isEqualTo(generator.generate(s2));
    }

    @Test
    @DisplayName("conteúdo diferente deve gerar hashes diferentes")
    void shouldGenerateDifferentHashesForDifferentContent() {
        CrawlerSnapshot s1 = buildSnapshot(List.of(
                new Movement(LocalDate.of(2025, 3, 15), "Conclusos para julgamento.")));
        CrawlerSnapshot s2 = buildSnapshot(List.of(
                new Movement(LocalDate.of(2025, 3, 16), "Julgamento realizado.")));

        assertThat(generator.generate(s1)).isNotEqualTo(generator.generate(s2));
    }

    @Test
    @DisplayName("espaços extras e maiúsculas não devem afetar o hash após normalização")
    void shouldBeInsensitiveToWhitespaceAndCase() {
        CrawlerSnapshot s1 = buildSnapshot(List.of(
                new Movement(LocalDate.of(2025, 1, 10), "Conclusos para julgamento.")));
        CrawlerSnapshot s2 = buildSnapshot(List.of(
                new Movement(LocalDate.of(2025, 1, 10), "  CONCLUSOS PARA JULGAMENTO.  ")));

        assertThat(generator.generate(s1)).isEqualTo(generator.generate(s2));
    }

    @Test
    @DisplayName("hash deve ter exatamente 64 caracteres hexadecimais (SHA-256)")
    void shouldProduceSha256Hash() {
        CrawlerSnapshot snapshot = buildSnapshot(List.of(
                new Movement(LocalDate.of(2025, 1, 1), "Distribuído.")));

        String hash = generator.generate(snapshot);
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("snapshot vazio (sem movimentos) deve gerar hash válido")
    void shouldHandleEmptyMovements() {
        CrawlerSnapshot snapshot = buildSnapshot(List.of());
        assertThat(generator.generate(snapshot)).hasSize(64);
    }

    @Test
    @DisplayName("ordem dos movimentos não deve afetar o hash (sort canônico)")
    void shouldBeOrderIndependent() {
        Movement m1 = new Movement(LocalDate.of(2025, 1, 1), "Distribuído.");
        Movement m2 = new Movement(LocalDate.of(2025, 3, 1), "Conclusos.");

        CrawlerSnapshot s1 = buildSnapshot(List.of(m1, m2));
        CrawlerSnapshot s2 = buildSnapshot(List.of(m2, m1));

        assertThat(generator.generate(s1)).isEqualTo(generator.generate(s2));
    }

    private CrawlerSnapshot buildSnapshot(List<Movement> movements) {
        return new CrawlerSnapshot(
                "0001234-55.2020.8.26.0001",
                "STF",
                "placeholder",
                "{}",
                movements,
                CrawlerStrategy.HTTP,
                "1.0.0",
                Instant.now()
        );
    }
}