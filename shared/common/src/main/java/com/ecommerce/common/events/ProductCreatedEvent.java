package com.ecommerce.common.events;

import java.time.Instant;
import java.util.UUID;

public record ProductCreatedEvent(String eventId, Long productId, String name, String sku, Instant timestamp) {

    public static ProductCreatedEvent of(Long productId, String name, String sku) {
        return new ProductCreatedEvent(UUID.randomUUID().toString(), productId, name, sku, Instant.now());
    }
}