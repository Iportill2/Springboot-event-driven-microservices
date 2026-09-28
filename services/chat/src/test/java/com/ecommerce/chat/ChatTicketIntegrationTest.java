package com.ecommerce.chat;

import com.ecommerce.chat.service.ChatIdentity;
import com.ecommerce.chat.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Comprueba el handshake de extremo a extremo contra un Tomcat real.
 *
 * <p>TicketService va simulado a proposito: aqui lo que se prueba es el cableado
 * del handshake (que un ticket valido completa el upgrade y entrega la identidad,
 * y que uno invalido lo aborta), no la semántica de un solo uso, que ya cubre
 * TicketServiceTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatTicketIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @MockitoBean
    private TicketService ticketService;

    @LocalServerPort
    private int port;

    // Spring Boot no registra un WebSocketClient por defecto, asi que se crea a mano.
    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final List<WebSocketSession> opened = new ArrayList<>();

    @AfterEach
    void closeOpenSessions() {
        opened.forEach(session -> {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception ignored) {
                // La sesion puede estar ya cerrada por el servidor.
            }
        });
        opened.clear();
    }

    @Test
    void aValidTicketCompletesTheHandshakeAndGreetsTheUser() throws Exception {
        when(ticketService.consume("bueno")).thenReturn(Optional.of(new ChatIdentity(7L, "ada")));

        TestClient probe = connect("bueno");
        assertThat(probe.open.await(5, TimeUnit.SECONDS)).isTrue();

        String welcome = probe.frames.poll(5, TimeUnit.SECONDS);
        assertThat(welcome).isNotNull();

        JsonNode frame = MAPPER.readTree(welcome);
        assertThat(frame.path("type").asText()).isEqualTo("connected");
        assertThat(frame.path("payload").path("userId").asLong()).isEqualTo(7L);
        assertThat(frame.path("payload").path("username").asText()).isEqualTo("ada");
    }

    @Test
    void theChannelAnswersAPing() throws Exception {
        when(ticketService.consume("bueno")).thenReturn(Optional.of(new ChatIdentity(7L, "ada")));

        TestClient probe = connect("bueno");
        assertThat(probe.open.await(5, TimeUnit.SECONDS)).isTrue();
        // Descarta el saludo para dejar la cola en el siguiente frame.
        probe.frames.poll(5, TimeUnit.SECONDS);

        probe.session.sendMessage(new TextMessage("{\"type\":\"ping\"}"));

        String pong = probe.frames.poll(5, TimeUnit.SECONDS);
        assertThat(pong).isNotNull();
        assertThat(MAPPER.readTree(pong).path("type").asText()).isEqualTo("pong");
    }

    @Test
    void anUnsupportedFrameIsAnsweredWithAnError() throws Exception {
        when(ticketService.consume("bueno")).thenReturn(Optional.of(new ChatIdentity(7L, "ada")));

        TestClient probe = connect("bueno");
        assertThat(probe.open.await(5, TimeUnit.SECONDS)).isTrue();
        probe.frames.poll(5, TimeUnit.SECONDS);

        probe.session.sendMessage(new TextMessage("{\"type\":\"inventado\"}"));

        String error = probe.frames.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(MAPPER.readTree(error).path("type").asText()).isEqualTo("error");
    }

    @Test
    void aMissingTicketRefusesTheUpgrade() {
        when(ticketService.consume("")).thenReturn(Optional.empty());

        // El interceptor devuelve false, el handshake se aborta con 403 y no se
        // abre ningun socket. Solo se comprueba que la conexion falla: el codigo
        // exacto lo decide el contenedor, no el codigo de aplicacion.
        assertThrows(ExecutionException.class, () -> connect(""));
    }

    @Test
    void anAlreadyUsedTicketRefusesTheUpgrade() throws Exception {
        // El mismo ticket reenviado no vale: si la primera conexion lo consumio,
        // la segunda se queda sin identidad.
        when(ticketService.consume("reutilizado"))
                .thenReturn(Optional.of(new ChatIdentity(7L, "ada")))
                .thenReturn(Optional.empty());

        TestClient first = connect("reutilizado");
        assertThat(first.open.await(5, TimeUnit.SECONDS)).isTrue();

        assertThrows(ExecutionException.class, () -> connect("reutilizado"));
    }

    private TestClient connect(String ticket) throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        CountDownLatch open = new CountDownLatch(1);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        URI uri = URI.create("ws://localhost:" + port + "/api/chat/ws?ticket=" + ticket);

        CompletableFuture<WebSocketSession> future = client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                open.countDown();
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                frames.offer(message.getPayload());
            }
        }, headers, uri);

        WebSocketSession session = future.get(5, TimeUnit.SECONDS);
        opened.add(session);
        return new TestClient(session, frames, open);
    }

    private record TestClient(WebSocketSession session, BlockingQueue<String> frames, CountDownLatch open) {
    }
}
