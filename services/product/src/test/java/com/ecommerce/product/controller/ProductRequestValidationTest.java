package com.ecommerce.product.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static ProductRequest validRequest() {
        return new ProductRequest("Laptop", "SKU-1", "Portátil", new BigDecimal("999.99"));
    }

    private static Set<String> invalidFields(ProductRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void aWellFormedRequestHasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void nameCannotBeBlank() {
        assertThat(invalidFields(new ProductRequest("  ", "SKU-1", "d", new BigDecimal("1.00"))))
                .contains("name");
    }

    @Test
    void nameCannotExceedOneHundredCharacters() {
        assertThat(invalidFields(new ProductRequest("x".repeat(101), "SKU-1", "d", new BigDecimal("1.00"))))
                .contains("name");
    }

    @Test
    void skuCannotBeBlank() {
        assertThat(invalidFields(new ProductRequest("Laptop", "", "d", new BigDecimal("1.00"))))
                .contains("sku");
    }

    @Test
    void skuCannotExceedFiftyCharacters() {
        assertThat(invalidFields(new ProductRequest("Laptop", "x".repeat(51), "d", new BigDecimal("1.00"))))
                .contains("sku");
    }

    @Test
    void descriptionCannotExceedFiveHundredCharacters() {
        assertThat(invalidFields(new ProductRequest("Laptop", "SKU-1", "x".repeat(501), new BigDecimal("1.00"))))
                .contains("description");
    }

    @Test
    void priceIsRequired() {
        assertThat(invalidFields(new ProductRequest("Laptop", "SKU-1", "d", null)))
                .contains("price");
    }

    @Test
    void priceBelowOneCentIsRejected() {
        // @DecimalMin("0.01"): un precio de 0 o negativo llegaria a la base de datos
        // y crearia un producto que no se puede comprar.
        assertThat(invalidFields(new ProductRequest("Laptop", "SKU-1", "d", BigDecimal.ZERO)))
                .contains("price");
        assertThat(invalidFields(new ProductRequest("Laptop", "SKU-1", "d", new BigDecimal("-5.00"))))
                .contains("price");
    }

    @Test
    void descriptionIsOptional() {
        assertThat(validator.validate(new ProductRequest("Laptop", "SKU-1", null, new BigDecimal("1.00")))).isEmpty();
    }
}
