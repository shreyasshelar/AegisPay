package com.aegispay.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtClaimsExtractor claimsExtractor;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                ActorContext.Actor actor = claimsExtractor.extract(jwt);

                String correlationId = request.getHeader("X-Correlation-ID");
                String traceParent   = request.getHeader("traceparent");

                ActorContext.Actor enrichedActor = actor.toBuilder()
                        .correlationId(correlationId)
                        .traceParent(traceParent)
                        .build();

                ActorContext.set(enrichedActor);

                if (actor.getUserId() != null) {
                    MDC.put("userId", actor.getUserId().toString());
                }
                if (actor.getRole() != null) {
                    MDC.put("role", actor.getRole());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            ActorContext.clear();
            MDC.remove("userId");
            MDC.remove("role");
        }
    }
}
