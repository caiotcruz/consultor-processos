package com.consultorprocessos.process;

import com.consultorprocessos.shared.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class ProcessControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL    = "/v1/processes";
    private static final String COURTS_URL  = "/v1/courts";
    private static final String TEST_EMAIL  = "proc-user@teste.com";
    private static final String TEST_PASS   = "Senha@123";

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clearCapturedEmails();
        activateCourt("STF");
        token = registerVerifyAndGetToken(TEST_EMAIL, TEST_PASS);
    }

    @Nested
    @DisplayName("POST /processes — tribunal disponível")
    class SubscribeAvailableCourtTests {

        @Test
        @DisplayName("deve cadastrar processo e retornar 201")
        void shouldReturn201WithValidData() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "processNumber": "%s",
                                  "courtCode":     "STF",
                                  "alias":         "Meu processo"
                                }
                            """.formatted(cnj(1))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subscriptionId").isNotEmpty())
                    .andExpect(jsonPath("$.data.processId").isNotEmpty())
                    .andExpect(jsonPath("$.data.processNumber")
                            .value("0009001-55.2025.8.26.0001"))
                    .andExpect(jsonPath("$.data.alias").value("Meu processo"))
                    .andExpect(jsonPath("$.data.court.code").value("STF"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("deve deduplícar processo quando dois usuários cadastrarem o mesmo")
        void shouldDeduplicateProcessForMultipleUsers() throws Exception {
            clearCapturedEmails();
            String tokenB = registerVerifyAndGetToken("user-b@teste.com", TEST_PASS);

            String bodyA = mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"STF"}
                            """.formatted(cnj(2))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String bodyB = mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + tokenB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"STF"}
                            """.formatted(cnj(2))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String subIdA = objectMapper.readTree(bodyA).path("data").path("subscriptionId").asText();
            String subIdB = objectMapper.readTree(bodyB).path("data").path("subscriptionId").asText();
            assertThat(subIdA).isNotEqualTo(subIdB);

            String procIdA = objectMapper.readTree(bodyA).path("data").path("processId").asText();
            String procIdB = objectMapper.readTree(bodyB).path("data").path("processId").asText();
            assertThat(procIdA).isEqualTo(procIdB);

            int processCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processes WHERE process_number = ?",
                Integer.class, cnj(2));
            assertThat(processCount).isEqualTo(1);
        }

        @Test
        @DisplayName("deve retornar 409 para subscription duplicada do mesmo usuário")
        void shouldReturn409ForDuplicateSubscription() throws Exception {
            mockMvc.perform(post(BASE_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"processNumber":"%s","courtCode":"STF"}
                    """.formatted(cnj(3))));

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"STF"}
                            """.formatted(cnj(3))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("deve aceitar número de processo em formato sem separadores")
        void shouldAcceptProcessNumberWithoutSeparators() throws Exception {
            String rawNumber = cnj(4).replaceAll("[^0-9]", "");

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"STF"}
                            """.formatted(rawNumber)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.processNumber").value(cnj(4)));
        }

        @Test
        @DisplayName("deve retornar 422 quando usuário atingir limite do plano")
        void shouldReturn422WhenPlanLimitReached() throws Exception {
            fillPlanLimit(token, 5);

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"STF"}
                            """.formatted(cnj(100))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("PROCESS_LIMIT_REACHED"));
        }

        @Test
        @DisplayName("deve retornar 400 para número de processo inválido")
        void shouldReturn400ForInvalidProcessNumber() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"12345","courtCode":"STF"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("PROCESS_NUMBER_INVALID"));
        }

        @Test
        @DisplayName("deve retornar 400 quando courtCode for ausente")
        void shouldReturn400WhenCourtCodeMissing() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s"}
                            """.formatted(cnj(5))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("POST /processes — tribunal indisponível")
    class SubscribeUnavailableCourtTests {

        @Test
        @DisplayName("deve retornar 202 para tribunal inativo")
        void shouldReturn202ForInactiveCourt() throws Exception {
            deactivateCourt("STF");

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"STF"}
                            """.formatted(cnj(6))))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.courtRequestId").isNotEmpty())
                    .andExpect(jsonPath("$.data.estimatedDays").value(7))
                    .andExpect(jsonPath("$.data.message").isNotEmpty());

            int reqCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM court_requests WHERE court_code = 'STF'",
                Integer.class);
            assertThat(reqCount).isEqualTo(1);
        }

        @Test
        @DisplayName("deve retornar 202 para tribunal completamente desconhecido")
        void shouldReturn202ForUnknownCourt() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"TRIBUNAL_INVALIDO"}
                            """.formatted(cnj(7))))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.courtRequestId").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("GET /processes")
    class ListProcessesTests {

        @Test
        @DisplayName("deve retornar lista vazia para usuário sem processos")
        void shouldReturnEmptyListForNewUser() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.meta.totalElements").value(0));
        }

        @Test
        @DisplayName("deve listar processo cadastrado com dados corretos")
        void shouldListRegisteredProcess() throws Exception {
            mockMvc.perform(post(BASE_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"processNumber":"%s","courtCode":"STF","alias":"Processo Teste"}
                    """.formatted(cnj(8))));

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].alias").value("Processo Teste"))
                    .andExpect(jsonPath("$.data[0].court.code").value("STF"))
                    .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data[0].active").value(true))
                    .andExpect(jsonPath("$.meta.totalElements").value(1));
        }

        @Test
        @DisplayName("filtro ?active=false deve retornar apenas subscriptions inativas")
        void shouldFilterByActive() throws Exception {
            String subId = cadastrarProcesso(cnj(9));
            mockMvc.perform(post(BASE_URL + "/" + subId + "/deactivate")
                    .header("Authorization", "Bearer " + token));

            mockMvc.perform(get(BASE_URL + "?active=true")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.totalElements").value(0));

            mockMvc.perform(get(BASE_URL + "?active=false")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.totalElements").value(1));
        }

        @Test
        @DisplayName("usuário não vê processos de outro usuário")
        void shouldNotSeeOtherUsersProcesses() throws Exception {
            clearCapturedEmails();
            String tokenC = registerVerifyAndGetToken("user-c@teste.com", TEST_PASS);

            mockMvc.perform(post(BASE_URL)
                    .header("Authorization", "Bearer " + tokenC)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"processNumber":"%s","courtCode":"STF"}
                    """.formatted(cnj(10))));

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("GET /processes/{id}")
    class GetProcessTests {

        @Test
        @DisplayName("deve retornar detalhe completo da subscription")
        void shouldReturnFullDetail() throws Exception {
            String subId = cadastrarProcesso(cnj(11));

            mockMvc.perform(get(BASE_URL + "/" + subId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.subscriptionId").value(subId))
                    .andExpect(jsonPath("$.data.court.code").value("STF"))
                    .andExpect(jsonPath("$.data.court.healthScore").value(100))
                    .andExpect(jsonPath("$.data.consecutiveErrors").value(0));
        }

        @Test
        @DisplayName("deve retornar 404 para subscription de outro usuário")
        void shouldReturn404ForOtherUsersSubscription() throws Exception {
            clearCapturedEmails();
            String tokenD = registerVerifyAndGetToken("user-d@teste.com", TEST_PASS);
            String subIdD = cadastrarProcessoComToken(cnj(12), tokenD);

            mockMvc.perform(get(BASE_URL + "/" + subIdD)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 404 para ID inexistente")
        void shouldReturn404ForNonExistentId() throws Exception {
            mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /processes/{id}")
    class UpdateAliasTests {

        @Test
        @DisplayName("deve atualizar alias com sucesso")
        void shouldUpdateAlias() throws Exception {
            String subId = cadastrarProcesso(cnj(13));

            mockMvc.perform(patch(BASE_URL + "/" + subId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"alias":"Novo Alias"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.alias").value("Novo Alias"));
        }

        @Test
        @DisplayName("alias null deve remover o alias existente")
        void shouldRemoveAliasWhenNull() throws Exception {
            String subId = cadastrarProcesso(cnj(14), "Alias Original");

            mockMvc.perform(patch(BASE_URL + "/" + subId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"alias":null}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.alias").doesNotExist());
        }

        @Test
        @DisplayName("alias maior que 200 chars deve retornar 400")
        void shouldReturn400ForLongAlias() throws Exception {
            String subId = cadastrarProcesso(cnj(15));
            String longAlias = "a".repeat(201);

            mockMvc.perform(patch(BASE_URL + "/" + subId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"alias":"%s"}
                            """.formatted(longAlias)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /processes/{id}/deactivate e /reactivate")
    class DeactivateReactivateTests {

        @Test
        @DisplayName("deve desativar e reativar subscription corretamente")
        void shouldDeactivateAndReactivate() throws Exception {
            String subId = cadastrarProcesso(cnj(16));

            mockMvc.perform(post(BASE_URL + "/" + subId + "/deactivate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false))
                    .andExpect(jsonPath("$.data.deactivatedAt").isNotEmpty());

            mockMvc.perform(post(BASE_URL + "/" + subId + "/reactivate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true))
                    .andExpect(jsonPath("$.data.deactivatedAt").doesNotExist());
        }

        @Test
        @DisplayName("deactivate deve ser idempotente")
        void shouldBeIdempotentOnDeactivate() throws Exception {
            String subId = cadastrarProcesso(cnj(17));

            mockMvc.perform(post(BASE_URL + "/" + subId + "/deactivate")
                    .header("Authorization", "Bearer " + token));

            mockMvc.perform(post(BASE_URL + "/" + subId + "/deactivate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        }

        @Test
        @DisplayName("reativação deve falhar se plano estiver cheio")
        void shouldFailReactivateWhenPlanFull() throws Exception {
            String subId = cadastrarProcesso(cnj(18));
            mockMvc.perform(post(BASE_URL + "/" + subId + "/deactivate")
                    .header("Authorization", "Bearer " + token));

            fillPlanLimit(token, 5);

            mockMvc.perform(post(BASE_URL + "/" + subId + "/reactivate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("PROCESS_LIMIT_REACHED"));
        }
    }

    @Nested
    @DisplayName("DELETE /processes/{id}")
    class DeleteTests {

        @Test
        @DisplayName("deve remover subscription e liberar vaga no plano")
        void shouldDeleteAndFreeSlot() throws Exception {
            fillPlanLimit(token, 5);

            String listBody = mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            String subId = objectMapper.readTree(listBody)
                    .path("data").get(0).path("subscriptionId").asText();

            mockMvc.perform(delete(BASE_URL + "/" + subId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"processNumber":"%s","courtCode":"STF"}
                            """.formatted(cnj(200))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("deve retornar 404 ao deletar subscription inexistente")
        void shouldReturn404ForNonExistentSubscription() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /processes/{id}/history")
    class HistoryTests {

        @Test
        @DisplayName("deve retornar lista vazia antes da Fase 7")
        void shouldReturnEmptyHistoryBeforePhase7() throws Exception {
            String subId = cadastrarProcesso(cnj(19));

            mockMvc.perform(get(BASE_URL + "/" + subId + "/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.meta.totalElements").value(0));
        }

        @Test
        @DisplayName("deve retornar 404 para subscription de outro usuário")
        void shouldReturn404ForOtherUsersSubscriptionHistory() throws Exception {
            clearCapturedEmails();
            String tokenE = registerVerifyAndGetToken("user-e@teste.com", TEST_PASS);
            String subIdE = cadastrarProcessoComToken(cnj(20), tokenE);

            mockMvc.perform(get(BASE_URL + "/" + subIdE + "/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /courts")
    class CourtsTests {

        @Test
        @DisplayName("deve listar tribunais ativos")
        void shouldListActiveCourts() throws Exception {
            mockMvc.perform(get(COURTS_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].code").exists())
                    .andExpect(jsonPath("$.data[0].healthScore").value(100));
        }

        @Test
        @DisplayName("GET /courts/{code} deve retornar tribunal específico")
        void shouldReturnSpecificCourt() throws Exception {
            mockMvc.perform(get(COURTS_URL + "/STF")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").value("STF"))
                    .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("GET /courts/{code} deve retornar 404 para código inexistente")
        void shouldReturn404ForUnknownCourtCode() throws Exception {
            mockMvc.perform(get(COURTS_URL + "/INEXISTENTE")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    private String cadastrarProcesso(String processNumber) throws Exception {
        return cadastrarProcesso(processNumber, null);
    }

    private String cadastrarProcesso(String processNumber, String alias) throws Exception {
        return cadastrarProcessoComToken(processNumber, alias, token);
    }

    private String cadastrarProcessoComToken(String processNumber, String token) throws Exception {
        return cadastrarProcessoComToken(processNumber, null, token);
    }

    private String cadastrarProcessoComToken(String processNumber,
                                             String alias,
                                             String authToken) throws Exception {
        String aliasJson = alias != null ? "\"" + alias + "\"" : "null";
        String responseBody = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "processNumber": "%s",
                              "courtCode":     "STF",
                              "alias":         %s
                            }
                        """.formatted(processNumber, aliasJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseBody)
                .path("data").path("subscriptionId").asText();
    }
}