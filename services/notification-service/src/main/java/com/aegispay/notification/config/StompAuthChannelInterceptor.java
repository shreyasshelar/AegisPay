package com.aegispay.notification.config;

import com.aegispay.notification.client.UserResolverService;
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
 * <h3>Principal name resolution — three paths</h3>
 * <ol>
 *   <li><b>Fast path</b>: JWT carries {@code aegispay_user_id} (write-back already completed)
 *       → use it directly, no network call.</li>
 *   <li><b>Slow path</b>: {@code aegispay_user_id} absent (first-session social-login user
 *       whose token was refreshed before {@link com.aegispay.user.service.KeycloakAdminService}
 *       write-back completed) → call {@code GET /api/v1/users/me} via {@link UserResolverService}
 *       to obtain the AegisPay domain UUID (~5 ms in-cluster).  This matches the UUID that
 *       {@link org.springframework.messaging.simp.SimpMessagingTemplate#convertAndSendToUser}
 *       uses as the routing key, ensuring personal notifications reach the right session.</li>
 *   <li><b>Last resort</b>: user-service unreachable → fall back to JWT {@code sub}.
 *       The subscription destination will not match the notification routing key, so
 *       real-time pushes are silently missed for this session.  The next token refresh
 *       that carries {@code aegispay_user_id} will fix it without any user action.</li>
 * </ol>
 *
 * <h3>Why the slow path is necessary</h3>
 * The auth.ts JWT callback retries token refresh up to 3× (4.5 s window) waiting for
 * {@code aegispay_user_id}.  If Keycloak write-back takes longer than the window, the
 * browser connects to WebSocket with a token that still lacks the claim.  Without the
 * slow path, Spring's STOMP broker sets the session principal to the Keycloak sub, but
 * {@code NotificationDispatcher} always calls
 * {@code convertAndSendToUser(aegisPayUUID, "/queue/notifications", ...)}.
 * The destination resolves to {@code /user/{aegisPayUUID}/queue/notifications}, which
 * never matches the session whose principal is the Keycloak sub — messages are dropped.
 *
 * <h3>Performance</h3>
 * The slow path only fires for first-session social-login users (JWT has no
 * {@code aegispay_user_id} yet).  For all other users the fast path is O(1).
 * The HTTP call adds ~5–20 ms to the WebSocket CONNECT handshake — acceptable given
 * CONNECT happens at most once per session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder         jwtDecoder;
    private final UserResolverService userResolverService;

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

            // ── Fast path: aegispay_user_id already in JWT (established user) ──────────
            String principalName = jwt.getClaimAsString("aegispay_user_id");

            if (principalName == null || principalName.isBlank()) {
                // ── Slow path: first-session social-login user ────────────────────────
                // writeUserAttributes is async; the token may not carry aegispay_user_id
                // yet if Keycloak write-back is still in flight.  Call /me to resolve the
                // AegisPay UUID so convertAndSendToUser routing works from the first session.
                log.debug("STOMP CONNECT: aegispay_user_id absent for sub={} — resolving via /me",
                        jwt.getSubject());
                principalName = userResolverService.resolveUserId(jwt);

                if (jwt.getSubject().equals(principalName)) {
                    // UserResolverService hit the last-resort fallback (user-service unreachable).
                    // Log at WARN so ops can detect degraded real-time notification delivery.
                    log.warn("STOMP CONNECT: /me fallback failed for sub={} — using Keycloak sub "
                            + "as principal; real-time notifications will be missed until next "
                            + "token refresh completes aegispay_user_id write-back", jwt.getSubject());
                } else {
                    log.debug("STOMP CONNECT: resolved principalName={} via /me for sub={}",
                            principalName, jwt.getSubject());
                }
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
