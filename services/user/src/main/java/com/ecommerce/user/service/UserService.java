package com.ecommerce.user.service;

import com.ecommerce.common.events.UserRegisteredEvent;
import com.ecommerce.common.security.JwtService;
import com.ecommerce.user.controller.AuthResponse;
import com.ecommerce.user.controller.LoginRequest;
import com.ecommerce.user.controller.RegisterRequest;
import com.ecommerce.user.domain.AppUser;
import com.ecommerce.user.domain.Role;
import com.ecommerce.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class UserService {

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserEventPublisher eventPublisher;
    private final MailService mailService;
    private final TokenService tokenService;
    private final long verificationTokenTtlHours;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, UserEventPublisher eventPublisher,
                       MailService mailService, TokenService tokenService,
                       @Value("${app.verify.token-ttl-hours:24}") long verificationTokenTtlHours) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
        this.mailService = mailService;
        this.tokenService = tokenService;
        this.verificationTokenTtlHours = verificationTokenTtlHours;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        String fullName = trimToNull(request.fullName());
        String phone = trimToNull(request.phone());

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already in use");
        }

        AppUser user = new AppUser(username, email, passwordEncoder.encode(request.password()), Role.USER, fullName, phone, request.newsletter());
        user.setEnabled(false);

        String rawToken = tokenService.generateRawToken();
        user.setVerificationTokenHash(tokenService.hash(rawToken));
        user.setVerificationTokenExpiresAt(Instant.now().plus(verificationTokenTtlHours, ChronoUnit.HOURS));

        userRepository.save(user);

        eventPublisher.userRegistered(UserRegisteredEvent.of(user.getId(), user.getUsername(), user.getEmail()));
        mailService.sendVerificationEmail(user.getEmail(), user.getUsername(), rawToken);
    }

    @Transactional
    public String verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("El enlace no es válido o ya fue utilizado");
        }
        AppUser user = userRepository.findByVerificationTokenHash(tokenService.hash(rawToken.trim()))
                .orElseThrow(() -> new IllegalArgumentException("El enlace no es válido o ya fue utilizado"));

        if (tokenService.isExpired(user.getVerificationTokenExpiresAt())) {
            throw new IllegalArgumentException("El enlace ha caducado. Pide reenviar el correo.");
        }

        user.setEnabled(true);
        user.setVerificationTokenHash(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);
        return user.getEmail();
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email.trim().toLowerCase())
                .filter(user -> !user.isEnabled())
                .ifPresent(user -> {
                    String rawToken = tokenService.generateRawToken();
                    user.setVerificationTokenHash(tokenService.hash(rawToken));
                    user.setVerificationTokenExpiresAt(Instant.now().plus(verificationTokenTtlHours, ChronoUnit.HOURS));
                    userRepository.save(user);
                    mailService.sendVerificationEmail(user.getEmail(), user.getUsername(), rawToken);
                });
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username().trim())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AppUser currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public AppUser findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    private AuthResponse toAuthResponse(AppUser user) {
        String token = jwtService.generateToken(user.getId(), user.getUsername(), List.of(user.getRole().name()));
        return new AuthResponse(token, user.getUsername(), user.getId(), List.of(user.getRole().name()));
    }
}