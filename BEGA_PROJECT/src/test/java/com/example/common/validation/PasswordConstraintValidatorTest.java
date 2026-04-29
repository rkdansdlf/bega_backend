package com.example.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordConstraintValidatorTest {

    private PasswordConstraintValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordConstraintValidator(
                new LocalDenylistCompromisedPasswordChecker(Set.of(
                        "password1!",
                        "qwerty123!",
                        "welcome123!",
                        "baseball123!",
                        "admin123!")));
    }

    @Test
    void allowsNullOrEmptyForNotBlankValidator() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() {
        assertThat(validator.isValid("Short1!", null)).isFalse();
    }

    @Test
    void rejectsPasswordLongerThanSeventyTwoCharacters() {
        String password = "Aa1!" + "a".repeat(69);

        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsPasswordsMissingRequiredComplexity() {
        assertThat(validator.isValid("lowercaseonly1!", null)).isFalse();
        assertThat(validator.isValid("UPPERCASEONLY1!", null)).isFalse();
        assertThat(validator.isValid("NoDigitsHere!", null)).isFalse();
        assertThat(validator.isValid("NoSpecials123", null)).isFalse();
    }

    @Test
    void rejectsCommonPasswordsCaseInsensitively() {
        assertThat(validator.isValid("Password1!", null)).isFalse();
        assertThat(validator.isValid("Qwerty123!", null)).isFalse();
        assertThat(validator.isValid("Welcome123!", null)).isFalse();
        assertThat(validator.isValid("Baseball123!", null)).isFalse();
        assertThat(validator.isValid("Admin123!", null)).isFalse();
    }

    @Test
    void acceptsPasswordThatMeetsLengthComplexityAndDenylistRules() {
        assertThat(validator.isValid("StrongPass123!", null)).isTrue();
    }
}
