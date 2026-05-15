package com.aegispay.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * STOMP channel interceptor that validates the Bearer token in the CONNECT frame
 * and sets the Spring principal so that {@code convertAndSendToUser} can route
 * messages to the correct WebSocket session.
 *
 * <p>The principal name is set to the {@code aegispay_user_id} claim if present,
 * otherwise falls back to the JWT {@code sub} claim (Keycloak user UUID).
 * This must match the userId used by the backend when calling
 * {@code SimpMessagingTemplate.convertAndSendToUser(userId, ...)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT received without valid Authorization header");
            return message;
        }

        try {
            String token = authHeader.substring(7);
            Jwt jwt = jwtDecoder.decode(token);

            // Prefer the AegisPay-internal user ID; fall back to Keycloak sub
            String principalName = jwt.getClaimAsString("aegispay_user_id");
            if (principalName == null || principalName.isBlank()) {
                principalName = jwt.getSubject();
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principalName,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            accessor.setUser(auth);
            log.debug("STOMP CONNECT authenticated: principalName={}", principalName);

        } catch (Exception e) {
            log.warn("STOMP CONNECT JWT validation failed: {}", e.getMessage());
            // Don't block — connection proceeds as anonymous; messages simply won't route
        }

        return message;
    }
}
