package com.aegispay.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Extracts JWT claims from the authenticated principal and injects them as trusted
 * downstream headers so services don't need to re-parse the JWT.
 *
 * Headers added:
 *   X-User-Id      — aegispay_user_id claim (or falls back to sub)
 *   X-User-Role    — primary role from aegispay_role (first non-offline_access role, else first)
 *   X-Tenant-Id    — aegispay_tenant_id claim
 *   X-Auth-Subject — raw JWT sub
 *
 * Uses ServerHttpRequestDecorator to avoid UnsupportedOperationException on
 * ReadOnlyHttpHeaders that occurs with ServerHttpRequest.mutate().header().
 */
@Slf4j
@Component
public class JwtRelayGatewayFilter implements GatewayFilter, Ordered {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLE  = "X-User-Role";
    private static final String HEADER_TENANT_ID  = "X-Tenant-Id";
    private static final String HEADER_AUTH_SUB   = "X-Auth-Subject";

    private static final String CLAIM_USER_ID   = "aegispay_user_id";
    private static final String CLAIM_ROLE      = "aegispay_role";
    private static final String CLAIM_TENANT_ID = "aegispay_tenant_id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    var jwt = jwtAuth.getToken();

                    LinkedMultiValueMap<String, String> mutableHeaders =
                            new LinkedMultiValueMap<>(exchange.getRequest().getHeaders());

                    String userId = jwt.getClaimAsString(CLAIM_USER_ID);
                    if (userId != null) mutableHeaders.set(HEADER_USER_ID, userId);

                    // aegispay_role is multivalued — pick primary (first non-offline role)
                    List<String> roles = jwt.getClaimAsStringList(CLAIM_ROLE);
                    if (roles != null && !roles.isEmpty()) {
                        String primaryRole = roles.stream()
                                .filter(r -> !r.equals("offline_access"))
                                .findFirst()
                                .orElse(roles.get(0));
                        mutableHeaders.set(HEADER_USER_ROLE, primaryRole);
                    }

                    String tenantId = jwt.getClaimAsString(CLAIM_TENANT_ID);
                    if (tenantId != null) mutableHeaders.set(HEADER_TENANT_ID, tenantId);

                    String sub = jwt.getSubject();
                    if (sub != null) mutableHeaders.set(HEADER_AUTH_SUB, sub);

                    log.debug("JWT relay: userId={} role={} tenantId={}", userId,
                              mutableHeaders.getFirst(HEADER_USER_ROLE), tenantId);

                    HttpHeaders newHeaders = new HttpHeaders(mutableHeaders);
                    ServerHttpRequest decoratedRequest =
                            new ServerHttpRequestDecorator(exchange.getRequest()) {
                                @Override
                                @NonNull
                                public HttpHeaders getHeaders() {
                                    return newHeaders;
                                }
                            };

                    return chain.filter(exchange.mutate().request(decoratedRequest).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return -180;
    }
}
