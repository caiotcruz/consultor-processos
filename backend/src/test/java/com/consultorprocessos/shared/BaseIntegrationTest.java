package com.consultorprocessos.shared;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.consultorprocessos.auth.service.LogAuthEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {


    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("consultorprocessos_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"));


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",  POSTGRES::getUsername);
        registry.add("spring.datasource.password",  POSTGRES::getPassword);

        registry.add("spring.flyway.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",      POSTGRES::getUsername);
        registry.add("spring.flyway.password",  POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");

        registry.add("spring.rabbitmq.host",     RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port",     () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }


    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected LogAuthEmailService logEmailService;

    protected String registerVerifyAndGetToken(String email, String password) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Usuário Teste","email":"%s","password":"%s"}
                """.formatted(email, password)))
                .andExpect(status().isCreated());

        String verificationToken = logEmailService.getLastTokenFor(
                email, LogAuthEmailService.EmailType.VERIFICATION);

        mockMvc.perform(post("/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"%s"}
                """.formatted(verificationToken)))
                .andExpect(status().isOk());

        return login(email, password);
    }

    protected String login(String email, String password) throws Exception {
        String responseBody = mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}
                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(responseBody)
                .path("data")
                .path("accessToken")
                .asText();
    }

    protected String loginAndGetRefreshToken(String email, String password) throws Exception {
        String responseBody = mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}
                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(responseBody)
                .path("data")
                .path("refreshToken")
                .asText();
    }

    protected void clearCapturedEmails() {
        logEmailService.clear();
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM notification_history");
        jdbcTemplate.execute("DELETE FROM crawler_executions");
        jdbcTemplate.execute("DELETE FROM process_history");
        jdbcTemplate.execute("DELETE FROM process_snapshots");
        jdbcTemplate.execute("DELETE FROM process_subscriptions");
        jdbcTemplate.execute("DELETE FROM court_health_scores");
        jdbcTemplate.execute("DELETE FROM court_requests");
        jdbcTemplate.execute("DELETE FROM password_resets");
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM processes");
        jdbcTemplate.execute("DELETE FROM users");
    }
}