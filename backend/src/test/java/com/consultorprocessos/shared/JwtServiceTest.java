package com.consultorprocessos.shared;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.entity.UserStatus;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.shared.config.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class JwtServiceTest {

    private JwtService     jwtService;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        jwtService = new JwtService(keyPair, 15);

        User user = new User();
        user.setId(UUID.randomUUID());
        setField(user, "id", UUID.randomUUID());
        user.setName("Teste");
        user.setEmail("teste@teste.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of("ROLE_USER"));

        userDetails = new UserDetailsImpl(user);
    }

    @Test
    @DisplayName("deve gerar token JWT válido para usuário ativo")
    void shouldGenerateValidToken() {
        String token = jwtService.generateAccessToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("deve extrair claims corretos do token gerado")
    void shouldExtractCorrectClaims() {
        String token  = jwtService.generateAccessToken(userDetails);
        Claims claims = jwtService.validateAndExtractClaims(token);

        assertThat(claims.getSubject())
                .isEqualTo(userDetails.getUserId().toString());
        assertThat(claims.get("email", String.class))
                .isEqualTo(userDetails.getUsername());
        assertThat(claims.get("roles"))
                .isNotNull();
    }

    @Test
    @DisplayName("deve rejeitar token adulterado")
    void shouldRejectTamperedToken() {
        String token    = jwtService.generateAccessToken(userDetails);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "assinatura_invalida";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar token de outra chave RSA")
    void shouldRejectTokenFromDifferentKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        JwtService otherService = new JwtService(gen.generateKeyPair(), 15);

        String tokenFromOtherKey = otherService.generateAccessToken(userDetails);

        assertThat(jwtService.isTokenValid(tokenFromOtherKey)).isFalse();
    }

    @Test
    @DisplayName("token expirado deve ser inválido")
    void shouldRejectExpiredToken() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        JwtService shortLivedService = new JwtService(gen.generateKeyPair(), 0); 

        String token = shortLivedService.generateAccessToken(userDetails);
        Thread.sleep(100);

        assertThat(shortLivedService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("validateAndExtractClaims deve lançar JwtException para token inválido")
    void shouldThrowForInvalidToken() {
        assertThatThrownBy(() -> jwtService.validateAndExtractClaims("token.invalido.aqui"))
                .isInstanceOf(JwtException.class);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}