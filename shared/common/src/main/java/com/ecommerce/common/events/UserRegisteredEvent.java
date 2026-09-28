package com.ecommerce.common.events;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(String eventId, Long userId, String username, String email, Instant timestamp) {

    public static UserRegisteredEvent of(Long userId, String username, String email) {
        return new UserRegisteredEvent(UUID.randomUUID().toString(), userId, username, email, Instant.now());
    }
}