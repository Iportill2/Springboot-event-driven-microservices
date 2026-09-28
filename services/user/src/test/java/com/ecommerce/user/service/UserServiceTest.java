package com.ecommerce.user.service;

import com.ecommerce.common.events.UserRegisteredEvent;
import com.ecommerce.common.security.JwtService;
import com.ecommerce.user.controller.AuthResponse;
import com.ecommerce.user.controller.LoginRequest;
import com.ecommerce.user.controller.RegisterRequest;
import com.ecommerce.user.domain.AppUser;
import com.ecommerce.user.domain.Role;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long VERIFICATION_TTL_HOURS = 24L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserEventPublisher eventPublisher;
    @Mock
    private MailService mailService;

    // TokenService es logica pura sin dependencias: se usa el real para que las
    // aserciones sobre el hash sean significativas y no una simulacion.
    private final TokenService tokenService = new TokenService();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtService, eventPublisher,
                mailService, tokenService, VERIFICATION_TTL_HOURS);
    }

    private static RegisterRequest registerRequest() {
        return new RegisterRequest("iker", "Iker@Example.COM", "secret123", "Iker Portillo", "+34 600 000 000", true);
    }

    private void givenSaveAssignsId() {
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
    }

    private AppUser capturedUser() {
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private String capturedRawToken() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationEmail(anyString(), anyString(), captor.capture());
        return captor.getValue();
    }

    // ---------- register ----------

    @Test
    void registerCreatesADisabledAccount() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(false);
        givenSaveAssignsId();

        userService.register(registerRequest());

        assertThat(capturedUser().isEnabled()).isFalse();
    }

    @Test
    void registerStoresOnlyTheHashOfTheVerificationToken() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(false);
        givenSaveAssignsId();

        userService.register(registerRequest());

        String rawToken = capturedRawToken();
        String storedHash = capturedUser().getVerificationTokenHash();
        assertThat(storedHash).isEqualTo(tokenService.hash(rawToken));
        assertThat(storedHash).isNotEqualTo(rawToken);
    }

    @Test
    void registerSetsAnExpiryForTheVerificationToken() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(false);
        givenSaveAssignsId();
        Instant before = Instant.now();

        userService.register(registerRequest());

        Instant expiresAt = capturedUser().getVerificationTokenExpiresAt();
        assertThat(expiresAt).isAfter(before.plus(VERIFICATION_TTL_HOURS - 1, ChronoUnit.HOURS));
    }

    @Test
    void registerNormalisesTheEmailToLowerCase() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(false);
        givenSaveAssignsId();

        userService.register(registerRequest());

        verify(userRepository).existsByEmail("iker@example.com");
        assertThat(capturedUser().getEmail()).isEqualTo("iker@example.com");
    }

    @Test
    void registerHashesThePassword() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        givenSaveAssignsId();

        userService.register(registerRequest());

        verify(passwordEncoder).encode("secret123");
        assertThat(capturedUser().getPassword()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void registerPublishesExactlyOneUserRegisteredEvent() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(false);
        givenSaveAssignsId();

        userService.register(registerRequest());

        ArgumentCaptor<UserRegisteredEvent> captor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher, times(1)).userRegistered(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().username()).isEqualTo("iker");
        assertThat(captor.getValue().email()).isEqualTo("iker@example.com");
    }

    @Test
    void registerSendsTheVerificationEmail() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(false);
        givenSaveAssignsId();

        userService.register(registerRequest());

        verify(mailService).sendVerificationEmail(eq("iker@example.com"), eq("iker"), anyString());
    }

    @Test
    void registerRejectsADuplicateUsernameWithoutSavingOrNotifying() {
        when(userRepository.existsByUsername("iker")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(AppUser.class));
        verifyNoInteractions(eventPublisher, mailService);
    }

    @Test
    void registerRejectsADuplicateEmailWithoutSavingOrNotifying() {
        when(userRepository.existsByUsername("iker")).thenReturn(false);
        when(userRepository.existsByEmail("iker@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(AppUser.class));
        verifyNoInteractions(eventPublisher, mailService);
    }

    // ---------- login ----------

    @Test
    void loginReturnsAJwtForAnEnabledAccount() {
        AppUser user = new AppUser("iker", "iker@example.com", "$2a$10$hashed", Role.ADMIN);
        user.setId(7L);
        when(userRepository.findByUsername("iker")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateToken(7L, "iker", List.of("ADMIN"))).thenReturn("jwt-token");

        AuthResponse response = userService.login(new LoginRequest("iker", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.roles()).containsExactly("ADMIN");
    }

    @Test
    void loginRejectsAPendingAccountWithTheSameMessageAsAWrongPassword() {
        AppUser user = new AppUser("iker", "iker@example.com", "$2a$10$hashed", Role.USER);
        user.setEnabled(false);
        when(userRepository.findByUsername("iker")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(new LoginRequest("iker", "secret123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");

        verifyNoInteractions(jwtService);
    }

    @Test
    void loginRejectsAWrongPassword() {
        AppUser user = new AppUser("iker", "iker@example.com", "$2a$10$hashed", Role.USER);
        when(userRepository.findByUsername("iker")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(new LoginRequest("iker", "wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");

        verifyNoInteractions(jwtService);
    }

    @Test
    void loginRejectsAnUnknownUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequest("ghost", "secret123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");
    }

    // ---------- verifyEmail ----------

    @Test
    void verifyEmailEnablesTheAccountAndBurnsTheToken() {
        String rawToken = "a".repeat(64);
        AppUser user = new AppUser("iker", "iker@example.com", "$2a$10$hashed", Role.USER);
        user.setEnabled(false);
        user.setVerificationTokenHash(tokenService.hash(rawToken));
        user.setVerificationTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findByVerificationTokenHash(tokenService.hash(rawToken)))
                .thenReturn(Optional.of(user));

        String email = userService.verifyEmail(rawToken);

        assertThat(email).isEqualTo("iker@example.com");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getVerificationTokenHash()).isNull();
        assertThat(user.getVerificationTokenExpiresAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailRejectsAnExpiredToken() {
        String rawToken = "b".repeat(64);
        AppUser user = new AppUser("iker", "iker@example.com", "$2a$10$hashed", Role.USER);
        user.setEnabled(false);
        user.setVerificationTokenExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(userRepository.findByVerificationTokenHash(tokenService.hash(rawToken)))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.verifyEmail(rawToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caducado");

        assertThat(user.isEnabled()).isFalse();
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void verifyEmailRejectsAnUnknownToken() {
        when(userRepository.findByVerificationTokenHash(tokenService.hash("unknown")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyEmail("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyEmailRejectsABlankToken() {
        assertThatThrownBy(() -> userService.verifyEmail("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userService.verifyEmail(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- resendVerification ----------

    @Test
    void resendVerificationRotatesTheTokenForAPendingAccount() {
        String previousHash = "old-hash";
        AppUser user = new AppUser("iker", "iker@example.com", "$2a$10$hashed", Role.USER);
        user.setEnabled(false);
        user.setVerificationTokenHash(previousHash);
        when(userRepository.findByEmail("iker@example.com")).thenReturn(Optional.of(user));

        userService.resendVerification("iker@example.com");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationEmail(eq("iker@example.com"), eq("iker"), captor.capture());
        assertThat(user.getVerificationTokenHash())
                .isNotEqualTo(previousHash)
                .isEqualTo(tokenService.hash(captor.getValue()));
    }

    @Test
    void resendVerificationDoesNothingForAnAlreadyVerifiedAccount() {
        AppUser user = new AppUser("iker", "iker@example.com", "$2a$10$hashed", Role.USER);
        when(userRepository.findByEmail("iker@example.com")).thenReturn(Optional.of(user));

        userService.resendVerification("iker@example.com");

        verifyNoInteractions(mailService);
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void resendVerificationDoesNothingForAnUnknownEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        userService.resendVerification("ghost@example.com");

        verifyNoInteractions(mailService);
    }
}
