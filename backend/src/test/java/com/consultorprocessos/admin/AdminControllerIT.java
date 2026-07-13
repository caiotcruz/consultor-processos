package com.consultorprocessos.admin;

import com.consultorprocessos.shared.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class AdminControllerIT extends BaseIntegrationTest {

    private static final String ADMIN_EMAIL = "admin-test@teste.com";
    private static final String ADMIN_PASS  = "Admin@123";
    private static final String USER_EMAIL  = "regular-user@teste.com";
    private static final String USER_PASS   = "Senha@123";

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        clearCapturedEmails();
        adminToken = registerAdminAndGetToken(ADMIN_EMAIL, ADMIN_PASS);
        clearCapturedEmails();
        userToken  = registerVerifyAndGetToken(USER_EMAIL, USER_PASS);
        activateCourt("STF");
    }

    @Nested
    @DisplayName("Segurança — endpoints admin requerem ROLE_ADMIN")
    class SecurityTests {

        @Test
        @DisplayName("GET /v1/admin/users deve retornar 403 para usuário comum")
        void shouldReturn403ForRegularUser() throws Exception {
            mockMvc.perform(get("/v1/admin/users")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /v1/admin/users deve retornar 401 sem token")
        void shouldReturn401WithoutToken() throws Exception {
            mockMvc.perform(get("/v1/admin/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /v1/admin/users deve retornar 200 para ROLE_ADMIN")
        void shouldReturn200ForAdmin() throws Exception {
            mockMvc.perform(get("/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET/PATCH /v1/admin/users")
    class AdminUserTests {

        @Test
        @DisplayName("deve listar todos os usuários")
        void shouldListAllUsers() throws Exception {
            mockMvc.perform(get("/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.meta.totalElements").value(
                            org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        }

        @Test
        @DisplayName("deve filtrar usuários por status ACTIVE")
        void shouldFilterByStatus() throws Exception {
            mockMvc.perform(get("/v1/admin/users?status=ACTIVE")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
        }

        @Test
        @DisplayName("deve suspender um usuário")
        void shouldSuspendUser() throws Exception {
            String listBody = mockMvc.perform(get("/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andReturn().getResponse().getContentAsString();

            String userId = objectMapper.readTree(listBody)
                    .path("data").elements().next().path("id").asText();

            mockMvc.perform(patch("/v1/admin/users/" + userId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"status":"SUSPENDED"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

            String auditBody = mockMvc.perform(get("/v1/admin/audit")
                            .header("Authorization", "Bearer " + adminToken))
                    .andReturn().getResponse().getContentAsString();
            String action = objectMapper.readTree(auditBody)
                    .path("data").get(0).path("action").asText();
            assertThat(action).isEqualTo("UPDATE_USER");
        }
    }

    @Nested
    @DisplayName("GET/PATCH /v1/admin/courts")
    class AdminCourtTests {

        @Test
        @DisplayName("deve listar TODOS os tribunais incluindo inativos")
        void shouldListAllCourtsIncludingInactive() throws Exception {
            deactivateCourt("STF");

            mockMvc.perform(get("/v1/admin/courts")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3));
        }

        @Test
        @DisplayName("deve desativar tribunal via PATCH")
        void shouldDeactivateCourt() throws Exception {
            mockMvc.perform(patch("/v1/admin/courts/STF")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"active":false}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));

            String status = jdbcTemplate.queryForObject(
                    "SELECT active::text FROM courts WHERE code='STF'", String.class);
            assertThat(status).isIn("false", "f");
        }

        @Test
        @DisplayName("deve atualizar rate limit do tribunal")
        void shouldUpdateRateLimit() throws Exception {
            mockMvc.perform(patch("/v1/admin/courts/STF")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"rateLimitPerMin":3}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rateLimitPerMin").value(3));
        }
    }

    @Nested
    @DisplayName("GET/POST/DELETE /v1/admin/dlq")
    class AdminDlqTests {

        @Test
        @DisplayName("deve listar mensagens pendentes no DLQ")
        void shouldListPendingDlqMessages() throws Exception {
            mockMvc.perform(get("/v1/admin/dlq")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("deve descartar mensagem do DLQ")
        void shouldDiscardDlqMessage() throws Exception {
            UUID dlqId = UUID.fromString(jdbcTemplate.queryForObject("""
                    INSERT INTO dlq_messages (process_number, court_code, retry_count)
                    VALUES ('0001234-55.2020.8.26.0001', 'STF', 3)
                    RETURNING id::text
                    """, String.class));

            mockMvc.perform(delete("/v1/admin/dlq/" + dlqId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM dlq_messages WHERE id = ?",
                    String.class, dlqId);
            assertThat(status).isEqualTo("DISCARDED");
        }
    }

    @Nested
    @DisplayName("GET /v1/admin/health")
    class AdminHealthTests {

        @Test
        @DisplayName("deve retornar health detalhado com informações dos tribunais")
        void shouldReturnDetailedHealth() throws Exception {
            mockMvc.perform(get("/v1/admin/health")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.overallStatus").value("UP"))
                    .andExpect(jsonPath("$.data.courts").isArray())
                    .andExpect(jsonPath("$.data.queues.dlqPending").isNumber())
                    .andExpect(jsonPath("$.data.scheduler").exists());
        }
    }

    @Nested
    @DisplayName("GET /v1/admin/audit")
    class AuditTests {

        @Test
        @DisplayName("deve listar entradas de auditoria")
        void shouldListAuditEntries() throws Exception {
            mockMvc.perform(patch("/v1/admin/courts/STF")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"active":false}
                    """));

            mockMvc.perform(get("/v1/admin/audit")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.totalElements")
                            .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.data[0].action").value("UPDATE_COURT"))
                    .andExpect(jsonPath("$.data[0].actorEmail").value(ADMIN_EMAIL));
        }
    }
}