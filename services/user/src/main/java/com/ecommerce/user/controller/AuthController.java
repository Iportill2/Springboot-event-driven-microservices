package com.ecommerce.user.controller;

import com.ecommerce.common.api.ApiResponse;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResponse.ok("Cuenta creada. Revisa tu correo para activarla.", null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Login successful", userService.login(request));
    }

    @GetMapping("/verify")
    public ApiResponse<String> verify(@RequestParam("token") String token) {
        String email = userService.verifyEmail(token);
        return ApiResponse.ok("Cuenta verificada. Ya puedes iniciar sesión.", email);
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        userService.resendVerification(request.email());
        return ApiResponse.ok("Si la cuenta existe y no está verificada, recibirás un correo.", null);
    }
}