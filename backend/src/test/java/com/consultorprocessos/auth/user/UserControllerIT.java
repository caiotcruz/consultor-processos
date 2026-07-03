package com.consultorprocessos.auth.user;

import com.consultorprocessos.shared.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class UserControllerIT extends BaseIntegrationTest {

    private static final String ME_URL              = "/v1/users/me";
    private static final String CHANGE_PASSWORD_URL = "/v1/users/me/change-password";
    private static final String DELETE_URL          = "/v1/users/me";

    private static final String TEST_EMAIL    = "usuario@teste.com";
    private static final String TEST_PASSWORD = "Senha@123";

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        clearCapturedEmails();
        accessToken = registerVerifyAndGetToken(TEST_EMAIL, TEST_PASSWORD);
    }

    @Nested
    @DisplayName("GET /users/me")
    class GetProfileTests {

        @Test
        @DisplayName("deve retornar perfil completo do usuário autenticado")
        void shouldReturnFullProfile() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.plan.name").value("GRATUITO"))
                    .andExpect(jsonPath("$.data.plan.maxProcesses").value(5))
                    .andExpect(jsonPath("$.data.plan.checkIntervalHours").value(12))
                    .andExpect(jsonPath("$.data.usage.activeProcesses").value(0))
                    .andExpect(jsonPath("$.data.usage.remainingProcesses").value(5))
                    .andExpect(jsonPath("$.data.notifications.emailEnabled").value(true))
                    .andExpect(jsonPath("$.data.notifications.pushEnabled").value(false))
                    .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
        }

        @Test
        @DisplayName("deve retornar 401 sem token de autenticação")
        void shouldReturn401WithoutToken() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("deve retornar 401 com token inválido")
        void shouldReturn401WithInvalidToken() throws Exception {
            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer token.invalido.aqui"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("remainingProcesses deve ser null para plano ilimitado")
        void shouldReturnNullRemainingForUnlimitedPlan() throws Exception {
            jdbcTemplate.update(
                "UPDATE users u SET plan_id = (SELECT id FROM plans WHERE name = 'AVANCADO') " +
                "WHERE u.email = ?", TEST_EMAIL);

            mockMvc.perform(get(ME_URL)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.plan.maxProcesses").doesNotExist())
                    .andExpect(jsonPath("$.data.usage.remainingProcesses").doesNotExist());
        }
    }

    @Nested
    @DisplayName("PATCH /users/me")
    class UpdateProfileTests {

        @Test
        @DisplayName("deve atualizar o nome do usuário")
        void shouldUpdateName() throws Exception {
            mockMvc.perform(patch(ME_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Novo Nome Completo"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Novo Nome Completo"));

            String nameInDb = jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE email = ?", String.class, TEST_EMAIL);
            assertThat(nameInDb).isEqualTo("Novo Nome Completo");
        }

        @Test
        @DisplayName("deve atualizar preferências de notificação")
        void shouldUpdateNotificationPreferences() throws Exception {
            mockMvc.perform(patch(ME_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "notifications": {
                                    "emailEnabled": false,
                                    "pushEnabled":  true
                                  }
                                }
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notifications.emailEnabled").value(false))
                    .andExpect(jsonPath("$.data.notifications.pushEnabled").value(true));
        }

        @Test
        @DisplayName("deve atualizar nome e preferências simultaneamente")
        void shouldUpdateNameAndPreferencesTogether() throws Exception {
            mockMvc.perform(patch(ME_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "name": "Nome Atualizado",
                                  "notifications": {"pushEnabled": true}
                                }
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Nome Atualizado"))
                    .andExpect(jsonPath("$.data.notifications.pushEnabled").value(true))
                    .andExpect(jsonPath("$.data.notifications.emailEnabled").value(true));
        }

        @Test
        @DisplayName("body vazio deve retornar 200 sem alterar dados")
        void shouldReturn200ForEmptyBody() throws Exception {
            mockMvc.perform(patch(ME_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value(TEST_EMAIL));
        }

        @Test
        @DisplayName("nome com menos de 2 caracteres deve retornar 400")
        void shouldReturn400ForShortName() throws Exception {
            mockMvc.perform(patch(ME_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"A"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("espaços no início e fim do nome devem ser removidos")
        void shouldTrimNameWhitespace() throws Exception {
            mockMvc.perform(patch(ME_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"  Nome Com Espaços  "}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Nome Com Espaços"));
        }
    }

    @Nested
    @DisplayName("POST /users/me/change-password")
    class ChangePasswordTests {

        @Test
        @DisplayName("deve alterar senha com credenciais válidas")
        void shouldChangePasswordWithValidCredentials() throws Exception {
            mockMvc.perform(post(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword":     "NovaSenha@456"
                                }
                            """.formatted(TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.message").isNotEmpty());

            clearCapturedEmails();
            String newToken = login(TEST_EMAIL, "NovaSenha@456");
            assertThat(newToken).isNotBlank();
        }

        @Test
        @DisplayName("deve retornar 400 para senha atual incorreta")
        void shouldReturn400ForWrongCurrentPassword() throws Exception {
            mockMvc.perform(post(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "currentPassword": "SenhaErrada@999",
                                  "newPassword":     "NovaSenha@456"
                                }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("deve retornar 400 quando nova senha for igual à atual")
        void shouldReturn400WhenNewPasswordSameAsCurrent() throws Exception {
            mockMvc.perform(post(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword":     "%s"
                                }
                            """.formatted(TEST_PASSWORD, TEST_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("nova senha sem número deve retornar 400")
        void shouldReturn400ForNewPasswordWithoutNumber() throws Exception {
            mockMvc.perform(post(CHANGE_PASSWORD_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword":     "SemNumerosAqui!"
                                }
                            """.formatted(TEST_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[0].field").value("newPassword"));
        }

        @Test
        @DisplayName("refresh tokens devem ser revogados após troca de senha")
        void shouldRevokeAllRefreshTokensAfterPasswordChange() throws Exception {
            String refreshToken = loginAndGetRefreshToken(TEST_EMAIL, TEST_PASSWORD);

            mockMvc.perform(post(CHANGE_PASSWORD_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "currentPassword": "%s",
                          "newPassword":     "NovaSenha@456"
                        }
                    """.formatted(TEST_PASSWORD)));

            mockMvc.perform(post("/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"refreshToken":"%s"}
                            """.formatted(refreshToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }
    }

    @Nested
    @DisplayName("DELETE /users/me")
    class DeleteAccountTests {

        @Test
        @DisplayName("deve excluir conta com dados válidos")
        void shouldDeleteAccountWithValidData() throws Exception {
            mockMvc.perform(delete(DELETE_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "password":      "%s",
                                  "confirmPhrase": "DELETAR MINHA CONTA"
                                }
                            """.formatted(TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            String emailInDb = jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE email LIKE 'deleted_%'",
                String.class);
            assertThat(emailInDb).startsWith("deleted_");

            String statusInDb = jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE email = ?", String.class, emailInDb);
            assertThat(statusInDb).isEqualTo("DELETED");

            String nameInDb = jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE email = ?", String.class, emailInDb);
            assertThat(nameInDb).isEqualTo("Usuário Removido");
        }

        @Test
        @DisplayName("deve retornar 400 para senha incorreta")
        void shouldReturn400ForWrongPassword() throws Exception {
            mockMvc.perform(delete(DELETE_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "password":      "SenhaErrada@999",
                                  "confirmPhrase": "DELETAR MINHA CONTA"
                                }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

            String statusInDb = jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE email = ?", String.class, TEST_EMAIL);
            assertThat(statusInDb).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("deve retornar 400 para frase de confirmação incorreta")
        void shouldReturn400ForWrongConfirmPhrase() throws Exception {
            mockMvc.perform(delete(DELETE_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "password":      "%s",
                                  "confirmPhrase": "deletar minha conta"
                                }
                            """.formatted(TEST_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("frase de confirmação em minúsculas deve ser rejeitada")
        void shouldRejectLowercaseConfirmPhrase() throws Exception {
            mockMvc.perform(delete(DELETE_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "password":      "%s",
                                  "confirmPhrase": "DELETAR minha CONTA"
                                }
                            """.formatted(TEST_PASSWORD)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("após exclusão, login deve falhar")
        void shouldNotBeAbleToLoginAfterDeletion() throws Exception {
            mockMvc.perform(delete(DELETE_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "password":      "%s",
                          "confirmPhrase": "DELETAR MINHA CONTA"
                        }
                    """.formatted(TEST_PASSWORD)));

            mockMvc.perform(post("/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s","password":"%s"}
                            """.formatted(TEST_EMAIL, TEST_PASSWORD)))
                    .andExpect(status().isUnauthorized());
        }
    }
}