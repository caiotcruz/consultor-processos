package com.consultorprocessos.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class PasswordHashServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    @DisplayName("senha correta deve ser verificada com sucesso")
    void shouldMatchCorrectPassword() {
        String raw  = "MinhaS3nha!";
        String hash = encoder.encode(raw);

        assertThat(encoder.matches(raw, hash)).isTrue();
    }

    @Test
    @DisplayName("senha incorreta não deve corresponder ao hash")
    void shouldNotMatchIncorrectPassword() {
        String hash = encoder.encode("MinhaS3nha!");

        assertThat(encoder.matches("SenhaErrada!", hash)).isFalse();
    }

    @Test
    @DisplayName("mesma senha deve gerar hashes diferentes (salt aleatório)")
    void shouldGenerateDifferentHashesForSamePassword() {
        String raw   = "MinhaS3nha!";
        String hash1 = encoder.encode(raw);
        String hash2 = encoder.encode(raw);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(encoder.matches(raw, hash1)).isTrue();
        assertThat(encoder.matches(raw, hash2)).isTrue();
    }

    @Test
    @DisplayName("hash bcrypt deve ter custo 12")
    void shouldUseCost12() {
        String hash = encoder.encode("qualquerSenha1");

        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    @DisplayName("string vazia não deve corresponder a hash de senha real")
    void shouldNotMatchEmptyString() {
        String hash = encoder.encode("MinhaS3nha!");

        assertThat(encoder.matches("", hash)).isFalse();
    }
}