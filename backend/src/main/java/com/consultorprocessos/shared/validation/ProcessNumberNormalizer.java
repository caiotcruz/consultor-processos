package com.consultorprocessos.shared.validation;

import com.consultorprocessos.process.exception.InvalidProcessNumberException;
import org.springframework.stereotype.Component;

@Component
public class ProcessNumberNormalizer {

    /**
     * Formato CNJ: NNNNNNN-DD.AAAA.J.TT.OOOO
     *
     * 7  dígitos → número do processo
     * 2  dígitos → dígito verificador
     * 4  dígitos → ano
     * 1  dígito  → justiça (1–9)
     * 2  dígitos → tribunal
     * 4  dígitos → origem
     * Total: 20 dígitos
     *
     * Aceita qualquer separador entre os grupos.
     * Exemplos válidos:
     *   0001234-55.2020.8.26.0001  (já normalizado)
     *   00012345520208260001        (só dígitos)
     *   0001234.55.2020.8.26.0001  (ponto no hífen)
     *   0001234 55 2020 8 26 0001  (espaços)
     */
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidProcessNumberException(
                "Número do processo não pode ser vazio.");
        }

        String digits = input.replaceAll("[^0-9]", "");

        if (digits.length() != 20) {
            throw new InvalidProcessNumberException(String.format(
                "Número de processo inválido: esperados 20 dígitos, encontrados %d. " +
                "Formato esperado: NNNNNNN-DD.AAAA.J.TT.OOOO",
                digits.length()));
        }

        return String.format("%s-%s.%s.%s.%s.%s",
            digits.substring(0, 7),
            digits.substring(7, 9), 
            digits.substring(9, 13),
            digits.substring(13, 14),
            digits.substring(14, 16),
            digits.substring(16, 20)
        );
    }

    public boolean isValid(String input) {
        try {
            normalize(input);
            return true;
        } catch (InvalidProcessNumberException e) {
            return false;
        }
    }
}