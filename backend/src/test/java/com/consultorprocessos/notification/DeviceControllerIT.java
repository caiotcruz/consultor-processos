package com.consultorprocessos.notification;

import com.consultorprocessos.notification.repository.UserDeviceTokenRepository;
import com.consultorprocessos.shared.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class DeviceControllerIT extends BaseIntegrationTest {

    @Autowired
    private UserDeviceTokenRepository deviceRepository;

    private static final String DEVICES_URL = "/v1/users/me/devices";
    private static final String TEST_EMAIL  = "device-user@teste.com";
    private static final String TEST_PASS   = "Senha@123";
    private static final String FCM_TOKEN   = "fcm-test-token-" + System.currentTimeMillis();

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        clearCapturedEmails();
        accessToken = registerVerifyAndGetToken(TEST_EMAIL, TEST_PASS);
    }

    @Nested
    @DisplayName("POST /v1/users/me/devices")
    class RegisterDeviceTests {

        @Test
        @DisplayName("deve registrar dispositivo Android e retornar 201")
        void shouldRegisterAndroidDevice() throws Exception {
            mockMvc.perform(post(DEVICES_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"%s","platform":"ANDROID"}
                            """.formatted(FCM_TOKEN)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(deviceRepository.findByToken(FCM_TOKEN)).isPresent();
        }

        @Test
        @DisplayName("deve retornar 201 de forma idempotente para token já registrado")
        void shouldBeIdempotentForExistingToken() throws Exception {
            mockMvc.perform(post(DEVICES_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s","platform":"ANDROID"}
                    """.formatted(FCM_TOKEN)));

            mockMvc.perform(post(DEVICES_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"%s","platform":"ANDROID"}
                            """.formatted(FCM_TOKEN)))
                    .andExpect(status().isCreated());

            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_device_tokens WHERE token = ?",
                Integer.class, FCM_TOKEN))
            .isEqualTo(1);

        }

        @Test
        @DisplayName("deve retornar 400 para plataforma inválida")
        void shouldReturn400ForInvalidPlatform() throws Exception {
            mockMvc.perform(post(DEVICES_URL)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"some-token","platform":"WINDOWS"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("deve retornar 401 sem token de autenticação")
        void shouldReturn401WithoutAuth() throws Exception {
            mockMvc.perform(post(DEVICES_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"token":"any-token","platform":"IOS"}
                            """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /v1/users/me/devices/{token}")
    class UnregisterDeviceTests {

        @Test
        @DisplayName("deve remover dispositivo registrado")
        void shouldUnregisterDevice() throws Exception {
            mockMvc.perform(post(DEVICES_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"token":"%s","platform":"IOS"}
                    """.formatted(FCM_TOKEN)));

            assertThat(deviceRepository.findByToken(FCM_TOKEN)).isPresent();

            mockMvc.perform(delete(DEVICES_URL + "/" + FCM_TOKEN)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(deviceRepository.findByToken(FCM_TOKEN)).isNotPresent();
        }

        @Test
        @DisplayName("deve retornar 200 mesmo para token inexistente (idempotente)")
        void shouldReturn200ForNonExistentToken() throws Exception {
            mockMvc.perform(delete(DEVICES_URL + "/token-que-nao-existe")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /v1/users/me/notifications")
    class NotificationHistoryTests {

        @Test
        @DisplayName("deve retornar lista vazia de notificações para novo usuário")
        void shouldReturnEmptyForNewUser() throws Exception {
            mockMvc.perform(get("/v1/users/me/notifications")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.meta.totalElements").value(0));
        }
    }
}