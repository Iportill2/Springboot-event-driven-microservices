package com.ecommerce.chat.controller;

import com.ecommerce.chat.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private TicketService tickets;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(tickets)).build();
    }

    @Test
    void mintsATicketForTheIdentityForwardedByTheGateway() throws Exception {
        when(tickets.mint(7L, "ada")).thenReturn("ticket-abc");
        when(tickets.ttlSeconds()).thenReturn(30L);

        mvc.perform(post("/api/chat/ws-ticket")
                        .header("X-User-Id", "7")
                        .header("X-Username", "ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticket").value("ticket-abc"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(30));
    }

    @Test
    void aRequestWithoutTheGatewayIdentityIsRefused() throws Exception {
        // El endpoint no es publico: sin X-User-Id, que solo el gateway anade tras
        // validar el JWT, no hay identidad que emitir.
        mvc.perform(post("/api/chat/ws-ticket"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fallsBackToAPlaceholderWhenTheGatewaySendsNoUsername() throws Exception {
        when(tickets.mint(7L, "desconocido")).thenReturn("ticket-abc");
        when(tickets.ttlSeconds()).thenReturn(30L);

        mvc.perform(post("/api/chat/ws-ticket").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").value("ticket-abc"));
    }
}
