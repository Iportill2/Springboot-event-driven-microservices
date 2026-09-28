package com.ecommerce.chat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private TicketService tickets;

    @BeforeEach
    void setUp() {
        // lenient: hay tests que devuelven antes de tocar Redis.
        lenient().when(redis.opsForValue()).thenReturn(values);
        tickets = new TicketService(redis, 30);
    }

    @Test
    void mintStoresTheIdentityUnderTheTicketWithTheConfiguredTtl() {
        String ticket = tickets.mint(7L, "ada");

        assertThat(ticket).isNotBlank();
        verify(values).set("chat:ticket:" + ticket, "7:ada", Duration.ofSeconds(30));
    }

    @Test
    void mintedTicketsAreNotPredictable() {
        assertThat(tickets.mint(1L, "ada")).isNotEqualTo(tickets.mint(1L, "ada"));
    }

    @Test
    void consumeReturnsTheIdentityAndBurnsTheTicket() {
        when(values.getAndDelete("chat:ticket:abc")).thenReturn("7:ada");

        assertThat(tickets.consume("abc")).contains(new ChatIdentity(7L, "ada"));
    }

    @Test
    void aTicketCanOnlyBeUsedOnce() {
        // getAndDelete es atomico: la segunda llamada ya no encuentra la clave.
        // Si se usara get + del por separado, dos handshakes simultaneos con el
        // mismo ticket podrian pasar los dos.
        when(values.getAndDelete("chat:ticket:abc")).thenReturn("7:ada").thenReturn(null);

        assertThat(tickets.consume("abc")).isPresent();
        assertThat(tickets.consume("abc")).isEmpty();
    }

    @Test
    void anExpiredOrUnknownTicketIsRejected() {
        when(values.getAndDelete(anyString())).thenReturn(null);

        assertThat(tickets.consume("caducado")).isEmpty();
    }

    @Test
    void garbageStoredInRedisDoesNotBecomeAnIdentity() {
        when(values.getAndDelete(anyString())).thenReturn("no-es-un-id:ada");

        assertThat(tickets.consume("x")).isEmpty();
    }

    @Test
    void aUsernameContainingColonsSurvivesTheRoundTrip() {
        // El id va delante y se parte por el primer separador, asi que un
        // usuario con dos puntos no queda truncado.
        when(values.getAndDelete("chat:ticket:x")).thenReturn("7:ada:lovelace");

        assertThat(tickets.consume("x")).contains(new ChatIdentity(7L, "ada:lovelace"));
    }

    @Test
    void malformedTicketsNeverReachRedis() {
        // Si se consultara Redis con basura de la URL, el endpoint de handshake
        // se convertiria en una forma de bombardear Redis.
        assertThat(tickets.consume(null)).isEmpty();
        assertThat(tickets.consume("")).isEmpty();
        assertThat(tickets.consume("   ")).isEmpty();
        assertThat(tickets.consume("x".repeat(129))).isEmpty();

        verifyNoInteractions(values);
    }

    @Test
    void ttlIsExposedForTheTicketResponse() {
        assertThat(tickets.ttlSeconds()).isEqualTo(30);
    }
}
