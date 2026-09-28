package com.ecommerce.user.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotBlank @Email @Size(max = 150) String email) {
}