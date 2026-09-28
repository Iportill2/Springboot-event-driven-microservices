package com.ecommerce.product.controller;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String sku,
        @Size(max = 500) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price) {
}