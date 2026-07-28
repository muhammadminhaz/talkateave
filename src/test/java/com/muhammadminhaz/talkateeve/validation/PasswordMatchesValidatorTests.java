package com.muhammadminhaz.talkateeve.validation;

import com.muhammadminhaz.talkateeve.dto.RegisterRequestDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordMatchesValidatorTests {

    private final PasswordMatchesValidator validator = new PasswordMatchesValidator();

    private RegisterRequestDTO dto(String password, String confirm) {
        RegisterRequestDTO dto = new RegisterRequestDTO();
        dto.setPassword(password);
        dto.setConfirmPassword(confirm);
        return dto;
    }

    @Test
    void isValid_trueWhenPasswordsMatch() {
        assertTrue(validator.isValid(dto("secret123", "secret123"), null));
    }

    @Test
    void isValid_falseWhenPasswordsDiffer() {
        assertFalse(validator.isValid(dto("secret123", "different"), null));
    }

    @Test
    void isValid_falseWhenPasswordNull() {
        assertFalse(validator.isValid(dto(null, "secret123"), null));
    }

    @Test
    void isValid_falseWhenConfirmNull() {
        assertFalse(validator.isValid(dto("secret123", null), null));
    }
}
