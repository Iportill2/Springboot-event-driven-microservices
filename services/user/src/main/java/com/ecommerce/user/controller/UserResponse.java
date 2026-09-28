package com.ecommerce.user.controller;

import com.ecommerce.user.domain.Role;

import java.time.Instant;

public record UserResponse(Long id, String username, String email, Role role, boolean enabled, Instant createdAt) {

    public static UserResponse from(com.ecommerce.user.domain.AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.isEnabled(), user.getCreatedAt());
    }
}