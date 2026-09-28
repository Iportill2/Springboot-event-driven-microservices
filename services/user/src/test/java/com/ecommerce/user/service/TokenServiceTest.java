package com.ecommerce.user.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService();

    @Test
    void rawTokenIsSixtyFourHexCharacters() {
        assertThat(tokenService.generateRawToken()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void rawTokensDoNotRepeat() {
        Set<String> tokens = IntStream.range(0, 500)
                .mapToObj(i -> tokenService.generateRawToken())
                .collect(Collectors.toSet());

        assertThat(tokens).hasSize(500);
    }

    @Test
    void hashIsSixtyFourHexCharacters() {
        assertThat(tokenService.hash("some-raw-token")).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void hashIsDeterministic() {
        assertThat(tokenService.hash("some-raw-token")).isEqualTo(tokenService.hash("some-raw-token"));
    }

    @Test
    void hashIsNotTheRawToken() {
        // El enlace de verificacion viaja en el correo; en la base de datos solo debe
        // quedar el hash, para que leer la tabla no permita reutilizar el enlace.
        assertThat(tokenService.hash("some-raw-token")).isNotEqualTo("some-raw-token");
    }

    @Test
    void differentTokensHashDifferently() {
        assertThat(tokenService.hash("token-a")).isNotEqualTo(tokenService.hash("token-b"));
    }

    @Test
    void aNullExpiryCountsAsExpired() {
        assertThat(tokenService.isExpired(null)).isTrue();
    }

    @Test
    void aPastExpiryIsExpired() {
        assertThat(tokenService.isExpired(Instant.now().minus(1, ChronoUnit.HOURS))).isTrue();
    }

    @Test
    void aFutureExpiryIsNotExpired() {
        assertThat(tokenService.isExpired(Instant.now().plus(1, ChronoUnit.HOURS))).isFalse();
    }
}
