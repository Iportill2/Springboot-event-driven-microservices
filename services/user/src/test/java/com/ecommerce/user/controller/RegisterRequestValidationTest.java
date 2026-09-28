package com.ecommerce.user.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<String> invalidFields(RegisterRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static RegisterRequest valid() {
        return new RegisterRequest("iker", "iker@example.com", "secret123", "Iker Portillo", "+34 600 000 000", true);
    }

    @Test
    void aWellFormedRequestHasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void usernameMustBeAtLeastThreeCharacters() {
        assertThat(invalidFields(new RegisterRequest("ab", "iker@example.com", "secret123", null, null, false)))
                .contains("username");
    }

    @Test
    void usernameCannotExceedFiftyCharacters() {
        assertThat(invalidFields(new RegisterRequest("x".repeat(51), "iker@example.com", "secret123", null, null, false)))
                .contains("username");
    }

    @Test
    void usernameCannotBeBlank() {
        assertThat(invalidFields(new RegisterRequest("  ", "iker@example.com", "secret123", null, null, false)))
                .contains("username");
    }

    @Test
    void emailMustBeWellFormed() {
        assertThat(invalidFields(new RegisterRequest("iker", "not-an-email", "secret123", null, null, false)))
                .contains("email");
    }

    @Test
    void emailCannotExceedOneHundredAndFiftyCharacters() {
        String longEmail = "a".repeat(145) + "@example.com";
        assertThat(invalidFields(new RegisterRequest("iker", longEmail, "secret123", null, null, false)))
                .contains("email");
    }

    @Test
    void passwordMustBeAtLeastEightCharacters() {
        assertThat(invalidFields(new RegisterRequest("iker", "iker@example.com", "short", null, null, false)))
                .contains("password");
    }

    @Test
    void passwordCannotExceedSeventyTwoCharacters() {
        // BCrypt solo traga los primeros 72 bytes: sin este limite, las diferencias a
        // partir del byte 72 se perderian y dos contrasenas distintas colarian.
        assertThat(invalidFields(new RegisterRequest("iker", "iker@example.com", "x".repeat(73), null, null, false)))
                .contains("password");
    }

    @Test
    void fullNameCannotExceedOneHundredAndTwentyCharacters() {
        assertThat(invalidFields(new RegisterRequest("iker", "iker@example.com", "secret123",
                "x".repeat(121), null, false)))
                .contains("fullName");
    }

    @Test
    void phoneMustMatchTheExpectedShape() {
        assertThat(invalidFields(new RegisterRequest("iker", "iker@example.com", "secret123",
                null, "not a phone", false)))
                .contains("phone");
    }

    @Test
    void anEmptyPhoneIsAccepted() {
        // El @Pattern permite el vacio a proposito: el telefono es opcional.
        assertThat(invalidFields(new RegisterRequest("iker", "iker@example.com", "secret123", null, "", false)))
                .doesNotContain("phone");
    }

    @Test
    void phoneCannotExceedThirtyCharacters() {
        String tooLongPhone = "+34 600 000 000 000 000 000 000";
        assertThat(tooLongPhone).hasSize(31);
        assertThat(invalidFields(new RegisterRequest("iker", "iker@example.com", "secret123",
                null, tooLongPhone, false)))
                .contains("phone");
    }

    @Test
    void profileFieldsAreOptional() {
        assertThat(validator.validate(new RegisterRequest("iker", "iker@example.com", "secret123", null, null, false)))
                .isEmpty();
    }
}
