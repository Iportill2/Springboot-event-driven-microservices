package com.ecommerce.product.controller;

import com.ecommerce.product.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(Long id, String name, String sku, String description, BigDecimal price,
                              boolean active, Instant createdAt, Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getSku(), product.getDescription(),
                product.getPrice(), product.isActive(), product.getCreatedAt(), product.getUpdatedAt());
    }
}