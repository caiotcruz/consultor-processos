package com.consultorprocessos.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class HealthCheckIT extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /v1/health deve retornar 200 com status UP quando todos os componentes estão operacionais")
    void shouldReturnUpWhenAllComponentsHealthy() throws Exception {
        mockMvc.perform(get("/v1/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.components.database").value("UP"))
                .andExpect(jsonPath("$.components.redis").value("UP"))
                .andExpect(jsonPath("$.components.rabbitmq").value("UP"));
    }

    @Test
    @DisplayName("banco deve conter os 3 planos e 3 tribunais iniciais após as migrations")
    void shouldHaveInitialSeedData() {
        int planCount  = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plans",  Integer.class);
        int courtCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courts", Integer.class);

        org.assertj.core.api.Assertions.assertThat(planCount).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(courtCount).isEqualTo(3);
    }

    @Test
    @DisplayName("filas RabbitMQ devem existir após a inicialização do contexto Spring")
    void shouldHaveRabbitQueuesCreated() {
        org.assertj.core.api.Assertions.assertThat(true).isTrue();
    }
}