package com.consultorprocessos.auth;

import com.consultorprocessos.auth.service.LogAuthEmailService;
import com.consultorprocessos.shared.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
public class AuthControllerIT extends BaseIntegrationTest {

    @Autowired
    private LogAuthEmailService emailService;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String REGISTER_URL    = "/v1/auth/register";
    private static final String VERIFY_URL      = "/v1/auth/verify-email";
    private static final String LOGIN_URL       = "/v1/auth/login";
    private static final String REFRESH_URL     = "/v1/auth/refresh";
    private static final String LOGOUT_URL      = "/v1/auth/logout";
    private static final String FORGOT_URL      = "/v1/auth/forgot-password";
    private static final String RESET_URL       = "/v1/auth/reset-password";
    private static final String RESEND_URL      = "/v1/auth/resend-verification";

    private static final String VALID_EMAIL    = "usuario@teste.com";
    private static final String VALID_PASSWORD = "Senha@123";
    private static final String VALID_NAME     = "Usuário Teste";

    @BeforeEach
    void clearEmails() {
        emailService.clear();
    }

    private void registerUser(String email, String name, String password) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, password)));
    }

    private String getVerificationToken(String email) {
        return emailService.getLastTokenFor(email,
                LogAuthEmailService.EmailType.VERIFICATION);
    }

    private void registerAndVerify(String email) throws Exception {
        registerUser(email, VALID_NAME, VALID_PASSWORD);
        String token = getVerificationToken(email);
        mockMvc.perform(post(VERIFY_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"%s"}
                """.formatted(token)));
    }

    private String loginAndGetRefreshToken(String email, String password) throws Exception {
        var result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","password":"%s"}
                        """.formatted(email, password)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.refreshToken");
    }

    @Nested
    @DisplayName("POST /auth/register")
    class RegisterTests {

        @Test
        @DisplayName("deve criar usuário e retornar 201")
        void shouldRegisterAndReturn201() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "name":     "%s",
                                  "email":    "%s",
                                  "password": "%s"
                                }
                            """.formatted(VALID_NAME, VALID_EMAIL, VALID_PASSWORD)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.message").isNotEmpty());

            assertThat(emailService.getSentEmails()).hasSize(1);
            assertThat(emailService.getSentEmails().get(0).to()).isEqualTo(VALID_EMAIL);
        }

        @Test
        @DisplayName("deve retornar 409 para e-mail duplicado")
        void shouldReturn409ForDuplicateEmail() throws Exception {
            registerUser(VALID_EMAIL, VALID_NAME, VALID_PASSWORD);

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Outro","email":"%s","password":"%s"}
                            """.formatted(VALID_EMAIL, VALID_PASSWORD)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("deve retornar 400 para senha sem número")
        void shouldReturn400ForPasswordWithoutNumber() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Teste","email":"x@x.com","password":"SemNumero!"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("password"));
        }

        @Test
        @DisplayName("deve retornar 400 para e-mail inválido")
        void shouldReturn400ForInvalidEmail() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Teste","email":"nao-e-email","password":"%s"}
                            """.formatted(VALID_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[0].field").value("email"));
        }

        @Test
        @DisplayName("deve normalizar e-mail para lowercase")
        void shouldNormalizeEmailToLowercase() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Teste","email":"UPPER@CASE.COM","password":"%s"}
                            """.formatted(VALID_PASSWORD)))
                    .andExpect(status().isCreated());

            boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE email = 'upper@case.com')",
                Boolean.class);
            assertThat(exists).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /auth/verify-email")
    class VerifyEmailTests {

        @Test
        @DisplayName("deve verificar e-mail com token válido")
        void shouldVerifyEmailWithValidToken() throws Exception {
            registerUser(VALID_EMAIL, VALID_NAME, VALID_PASSWORD);
            String token = getVerificationToken(VALID_EMAIL);

            mockMvc.perform(post(VERIFY_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"%s"}
                            """.formatted(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE email = ?", String.class, VALID_EMAIL);
            assertThat(status).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("deve retornar 400 para token inválido")
        void shouldReturn400ForInvalidToken() throws Exception {
            mockMvc.perform(post(VERIFY_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"token_completamente_invalido"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("token reutilizado deve retornar 400")
        void shouldReturn400ForReusedToken() throws Exception {
            registerUser(VALID_EMAIL, VALID_NAME, VALID_PASSWORD);
            String token = getVerificationToken(VALID_EMAIL);

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s"}
                    """.formatted(token)));

            mockMvc.perform(post(VERIFY_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"%s"}
                            """.formatted(token)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /auth/login")
    class LoginTests {

        @Test
        @DisplayName("deve retornar tokens para credenciais válidas")
        void shouldReturnTokensForValidCredentials() throws Exception {
            registerAndVerify(VALID_EMAIL);

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s","password":"%s"}
                            """.formatted(VALID_EMAIL, VALID_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresIn").value(900))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.user.email").value(VALID_EMAIL));
        }

        @Test
        @DisplayName("deve retornar 401 para senha incorreta")
        void shouldReturn401ForWrongPassword() throws Exception {
            registerAndVerify(VALID_EMAIL);

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s","password":"SenhaErrada@1"}
                            """.formatted(VALID_EMAIL)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("deve retornar 401 para e-mail não verificado")
        void shouldReturn401ForUnverifiedEmail() throws Exception {
            registerUser(VALID_EMAIL, VALID_NAME, VALID_PASSWORD);

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s","password":"%s"}
                            """.formatted(VALID_EMAIL, VALID_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
        }

        @Test
        @DisplayName("deve bloquear conta após 5 tentativas falhas")
        void shouldLockAccountAfter5FailedAttempts() throws Exception {
            registerAndVerify(VALID_EMAIL);

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","password":"Errada@123"}
                        """.formatted(VALID_EMAIL)));
            }

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s","password":"%s"}
                            """.formatted(VALID_EMAIL, VALID_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
        }

        @Test
        @DisplayName("deve retornar mensagem genérica para e-mail inexistente (anti-enumeração)")
        void shouldReturnGenericMessageForNonExistentEmail() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"naoexiste@nada.com","password":"Senha@123"}
                            """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("deve retornar novo par de tokens com refresh token válido")
        void shouldReturnNewTokensWithValidRefreshToken() throws Exception {
            registerAndVerify(VALID_EMAIL);
            String refreshToken = loginAndGetRefreshToken(VALID_EMAIL, VALID_PASSWORD);

            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"refreshToken":"%s"}
                            """.formatted(refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
        }

        @Test
        @DisplayName("refresh token usado não pode ser reutilizado (rotation)")
        void shouldNotAllowRefreshTokenReuse() throws Exception {
            registerAndVerify(VALID_EMAIL);
            String refreshToken = loginAndGetRefreshToken(VALID_EMAIL, VALID_PASSWORD);

            mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"refreshToken":"%s"}
                    """.formatted(refreshToken)));

            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"refreshToken":"%s"}
                            """.formatted(refreshToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("deve retornar 400 para refresh token inválido")
        void shouldReturn400ForInvalidRefreshToken() throws Exception {
            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"refreshToken":"token_invalido"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("deve revogar refresh token e retornar 200")
        void shouldRevokeRefreshTokenAndReturn200() throws Exception {
            registerAndVerify(VALID_EMAIL);
            String refreshToken = loginAndGetRefreshToken(VALID_EMAIL, VALID_PASSWORD);

            mockMvc.perform(post(LOGOUT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"refreshToken":"%s"}
                            """.formatted(refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"refreshToken":"%s"}
                            """.formatted(refreshToken)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("logout com token inexistente deve retornar 200 (idempotente)")
        void shouldReturn200ForNonExistentToken() throws Exception {
            mockMvc.perform(post(LOGOUT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"refreshToken":"token_que_nao_existe"}
                            """))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /auth/forgot-password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("deve retornar 200 para e-mail cadastrado (e enviar e-mail)")
        void shouldReturn200AndSendEmailForRegisteredUser() throws Exception {
            registerAndVerify(VALID_EMAIL);

            mockMvc.perform(post(FORGOT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s"}
                            """.formatted(VALID_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            long resetEmails = emailService.getSentEmails().stream()
                    .filter(e -> e.type() == LogAuthEmailService.EmailType.PASSWORD_RESET)
                    .count();
            assertThat(resetEmails).isEqualTo(1);
        }

        @Test
        @DisplayName("deve retornar 200 para e-mail não cadastrado (anti-enumeração)")
        void shouldReturn200ForNonExistentEmail() throws Exception {
            mockMvc.perform(post(FORGOT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"naoexiste@nada.com"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(emailService.getSentEmails()).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /auth/reset-password")
    class ResetPasswordTests {

        @Test
        @DisplayName("deve redefinir senha com token válido")
        void shouldResetPasswordWithValidToken() throws Exception {
            registerAndVerify(VALID_EMAIL);

            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"%s"}
                    """.formatted(VALID_EMAIL)));

            String resetToken = emailService.getLastTokenFor(
                    VALID_EMAIL, LogAuthEmailService.EmailType.PASSWORD_RESET);

            mockMvc.perform(post(RESET_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"%s","newPassword":"NovaSenha@456"}
                            """.formatted(resetToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s","password":"NovaSenha@456"}
                            """.formatted(VALID_EMAIL)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("token de reset não pode ser reutilizado")
        void shouldNotAllowTokenReuse() throws Exception {
            registerAndVerify(VALID_EMAIL);
            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"%s"}
                    """.formatted(VALID_EMAIL)));

            String resetToken = emailService.getLastTokenFor(
                    VALID_EMAIL, LogAuthEmailService.EmailType.PASSWORD_RESET);

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s","newPassword":"NovaSenha@456"}
                    """.formatted(resetToken)));

            mockMvc.perform(post(RESET_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"%s","newPassword":"OutraSenha@789"}
                            """.formatted(resetToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("deve retornar 400 para token inválido")
        void shouldReturn400ForInvalidToken() throws Exception {
            mockMvc.perform(post(RESET_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"token_invalido","newPassword":"NovaSenha@123"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }
    }

    @Nested
    @DisplayName("POST /auth/resend-verification")
    class ResendVerificationTests {

        @Test
        @DisplayName("deve reenviar e-mail e retornar 200")
        void shouldResendEmailAndReturn200() throws Exception {
            registerUser(VALID_EMAIL, VALID_NAME, VALID_PASSWORD);
            emailService.clear();

            mockMvc.perform(post(RESEND_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s"}
                            """.formatted(VALID_EMAIL)))
                    .andExpect(status().isOk());

            assertThat(emailService.getSentEmails()).hasSize(1);
        }

        @Test
        @DisplayName("deve retornar 200 mesmo para e-mail já verificado (anti-enumeração)")
        void shouldReturn200EvenForVerifiedEmail() throws Exception {
            registerAndVerify(VALID_EMAIL);
            emailService.clear();

            mockMvc.perform(post(RESEND_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"email":"%s"}
                            """.formatted(VALID_EMAIL)))
                    .andExpect(status().isOk());

            assertThat(emailService.getSentEmails()).isEmpty();
        }
    }
}