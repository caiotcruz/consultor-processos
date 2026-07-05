package com.consultorprocessos.shared;

import com.consultorprocessos.process.exception.InvalidProcessNumberException;
import com.consultorprocessos.shared.validation.ProcessNumberNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class ProcessNumberNormalizerTest {

    private final ProcessNumberNormalizer normalizer = new ProcessNumberNormalizer();

    private static final String EXPECTED = "0001234-55.2020.8.26.0001";

    @ParameterizedTest(name = "entrada: [{0}] → {1}")
    @CsvSource({
        "0001234-55.2020.8.26.0001, 0001234-55.2020.8.26.0001",
        "00012345520208260001,       0001234-55.2020.8.26.0001",
        "0001234.55.2020.8.26.0001, 0001234-55.2020.8.26.0001",
        "0001234 55 2020 8 26 0001, 0001234-55.2020.8.26.0001",
        "0001234/55/2020/8/26/0001, 0001234-55.2020.8.26.0001",
        "0001234_55_2020_8_26_0001, 0001234-55.2020.8.26.0001"
    })
    @DisplayName("deve normalizar formatos variados para CNJ padrão")
    void shouldNormalizeVariousFormats(String input, String expected) {
        assertThat(normalizer.normalize(input.strip())).isEqualTo(expected.strip());
    }

    @Test
    @DisplayName("deve normalizar número já no formato correto sem alteração")
    void shouldReturnSameWhenAlreadyNormalized() {
        assertThat(normalizer.normalize(EXPECTED)).isEqualTo(EXPECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "123456789", "0001234-55.2020.8.26", ""})
    @DisplayName("deve lançar exceção para números com quantidade incorreta de dígitos")
    void shouldThrowForInvalidDigitCount(String input) {
        assertThatThrownBy(() -> normalizer.normalize(input))
                .isInstanceOf(InvalidProcessNumberException.class);
    }

    @Test
    @DisplayName("deve lançar exceção para entrada nula")
    void shouldThrowForNullInput() {
        assertThatThrownBy(() -> normalizer.normalize(null))
                .isInstanceOf(InvalidProcessNumberException.class);
    }

    @Test
    @DisplayName("deve lançar exceção para entrada em branco")
    void shouldThrowForBlankInput() {
        assertThatThrownBy(() -> normalizer.normalize("   "))
                .isInstanceOf(InvalidProcessNumberException.class);
    }

    @Test
    @DisplayName("isValid deve retornar true para número válido")
    void isValidShouldReturnTrueForValid() {
        assertThat(normalizer.isValid(EXPECTED)).isTrue();
        assertThat(normalizer.isValid("00012345520208260001")).isTrue();
    }

    @Test
    @DisplayName("isValid deve retornar false para número inválido")
    void isValidShouldReturnFalseForInvalid() {
        assertThat(normalizer.isValid("12345")).isFalse();
        assertThat(normalizer.isValid(null)).isFalse();
        assertThat(normalizer.isValid("")).isFalse();
    }

    @Test
    @DisplayName("resultado deve ter sempre exatamente 25 caracteres")
    void normalizedResultShouldAlwaysHave25Characters() {
        String result = normalizer.normalize("00012345520208260001");
        assertThat(result).hasSize(25);
    }

    @Test
    @DisplayName("formato resultado deve ser NNNNNNN-DD.AAAA.J.TT.OOOO")
    void normalizedResultShouldMatchCnjPattern() {
        String result = normalizer.normalize("00012345520208260001");
        assertThat(result).matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4}");
    }
}