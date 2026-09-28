package com.ecommerce.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-for-jwt-service-unit-tests-0123456789";
    private static final String OTHER_SECRET = "a-completely-different-secret-key-abcdefghijklmnop";

    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L);

    @Test
    void generatedTokenCarriesSubjectUserIdAndRoles() {
        String token = jwtService.generateToken(42L, "iker", List.of("USER", "ADMIN"));

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("iker");
        assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
        assertThat(claims.get("roles", List.class)).containsExactly("USER", "ADMIN");
    }

    @Test
    void generatedTokenExposesIssuedAtAndExpiration() {
        long before = System.currentTimeMillis();

        Claims claims = jwtService.parse(jwtService.generateToken(1L, "iker", List.of("USER")));

        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new java.util.Date(before));
    }

    @Test
    void userIdClaimSurvivesTheRoundTripAsALong() {
        // El id se serializa como numero JSON, asi que al releerlo JJWT lo devuelve
        // como Integer. El gateway hace claims.get("userId", Long.class), asi que esto
        // fija que la conversion a Long funciona y no revienta con un id pequeno.
        Claims claims = jwtService.parse(jwtService.generateToken(7L, "iker", List.of("USER")));

        assertThat(claims.get("userId")).isInstanceOf(Number.class);
        assertThat(claims.get("userId", Long.class)).isEqualTo(7L);
    }

    @Test
    void isValidAcceptsAFreshlyGeneratedToken() {
        assertThat(jwtService.isValid(jwtService.generateToken(1L, "iker", List.of("USER")))).isTrue();
    }

    @Test
    void isValidRejectsATokenSignedWithADifferentSecret() {
        String foreignToken = new JwtService(OTHER_SECRET, 3_600_000L)
                .generateToken(1L, "attacker", List.of("ADMIN"));

        assertThat(jwtService.isValid(foreignToken)).isFalse();
    }

    @Test
    void isValidRejectsAnExpiredToken() {
        JwtService alreadyExpired = new JwtService(SECRET, -1_000L);

        assertThat(alreadyExpired.isValid(alreadyExpired.generateToken(1L, "iker", List.of("USER")))).isFalse();
    }

    @Test
    void isValidRejectsGarbageWithoutThrowing() {
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
        assertThat(jwtService.isValid("a.b.c")).isFalse();
    }

    @Test
    void parseThrowsOnATamperedToken() {
        String token = jwtService.generateToken(1L, "iker", List.of("USER"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }
}
