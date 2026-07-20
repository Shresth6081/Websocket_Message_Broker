package com.example.chatservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        List<String> tokenParams = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().get("token");

        if (tokenParams == null || tokenParams.isEmpty()) {
            log.warn("WebSocket connection rejected: missing token");
            return false;
        }

        String token = tokenParams.get(0);
        if (!jwtService.isTokenValid(token)) {
            log.warn("WebSocket connection rejected: invalid token");
            return false;
        }

        String username = jwtService.extractUsername(token);
        String displayName = jwtService.extractDisplayName(token);
        attributes.put("username", username);
        attributes.put("displayName", displayName != null ? displayName : username);
        log.info("WebSocket handshake accepted for user: {}", username);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
