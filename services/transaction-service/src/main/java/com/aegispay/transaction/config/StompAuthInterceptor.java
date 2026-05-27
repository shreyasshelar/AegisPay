package com.aegispay.transaction.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * STOMP channel interceptor that authenticates WebSocket clients.
 *
 * <p>Spring's HTTP security filter chain validates Bearer tokens on normal HTTP
 * requests, but the WebSocket HTTP upgrade does NOT carry a Bearer header — the
 * token arrives inside the STOMP CONNECT frame instead.  This interceptor bridges
 * that gap by:
 * <ol>
 *   <li>Detecting the CONNECT frame on the inbound channel.</li>
 *   <li>Extracting the {@code Authorization: Bearer <token>} native header.</li>
 *   <li>Decoding and validating the JWT via the auto-configured {@link JwtDecoder}.</li>
 *   <li>Converting it to an {@code Authentication} and setting it as the frame's
 *       user principal via {@link StompHeaderAccessor#setUser}, so Spring's STOMP
 *       broker can route {@code /user/…} destinations correctly.</li>
 * </ol>
 *
 * <p>Connections without a valid token are rejected immediately with a
 * {@link MessageDeliveryException} (the STOMP ERROR frame is sent back
 * to the client and the session is closed).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder                jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Only validate on CONNECT; all other frames ride the already-authenticated session
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT rejected — missing or malformed Authorization header");
            throw new MessageDeliveryException(message, "Missing Authorization header");
        }

        String token = authHeader.substring(7).trim();
        try {
            Jwt jwt = jwtDecoder.decode(token);
            var auth = jwtAuthenticationConverter.convert(jwt);
            if (auth != null) {
                auth.setAuthenticated(true);
                // Setting the user here makes convertAndSendToUser() routing work
                // and allows @SendToUser destinations to resolve the principal name.
                accessor.setUser(auth);
                log.debug("STOMP CONNECT authenticated: principal={}", auth.getName());
            }
        } catch (JwtException ex) {
            log.warn("STOMP CONNECT rejected — invalid JWT: {}", ex.getMessage());
            throw new MessageDeliveryException(message, "Invalid JWT: " + ex.getMessage());
        }

        return message;
    }
}
