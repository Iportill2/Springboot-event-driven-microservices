package com.ecommerce.user.controller;

import java.util.List;

public record AuthResponse(String token, String username, Long userId, List<String> roles) {
}