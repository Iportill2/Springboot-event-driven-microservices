package com.ecommerce.gateway.filter;

import com.ecommerce.common.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/users/login",
            "/api/users/register",
            "/api/users/verify",
            "/api/users/resend-verification"
    );

    /**
     * El handshake del chat se autentica con un ticket de un solo uso, no con el
     * JWT: el navegador no puede enviar cabeceras en un WebSocket, asi que el
     * Bearer nunca llega. Se exime solo esta ruta exacta y nunca el prefijo
     * entero, para que /api/chat/ws-ticket siga exigiendo un token valido.
     */
    private static final String WS_PATH = "/api/chat/ws";

    private final JwtService jwtService;

    public JwtAuthGlobalFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder request = exchange.getRequest().mutate();
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        boolean wsUpgrade = path.equals(WS_PATH) || path.startsWith(WS_PATH + "/");
        boolean publicPath = wsUpgrade
                || PUBLIC_PATHS.stream().anyMatch(path::endsWith)
                || (method == HttpMethod.GET && path.startsWith("/api/products"));

        if (publicPath) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isValid(token)) {
                Claims claims = jwtService.parse(token);
                request.header("X-User-Id", String.valueOf(claims.get("userId", Long.class)))
                        .header("X-Username", claims.getSubject())
                        .header("X-User-Roles", String.join(",", claims.get("roles", List.class)));
                return chain.filter(exchange.mutate().request(request.build()).build());
            }
        }

        return unauthorized(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"Unauthorized\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}