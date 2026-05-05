package com.aegispay.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts JWT claims from the authenticated principal (already validated by Spring Security)
 * and injects them as trusted downstream headers.
 *
 * Downstream services read these headers directly instead of re-parsing the JWT,
 * but MUST still validate the Bearer token for zero-trust compliance.
 *
 * Headers added:
 *   X-User-Id        — internal UUID from claim `aegispay_user_id`
 *   X-User-Role      — role name from claim `aegispay_role`
 *   X-Tenant-Id      — tenant from claim `aegispay_tenant_id`
 *   X-Auth-Subject   — raw JWT `sub` (IdP-level identity)
 *
 * The original Authorization header is preserved and forwarded unchanged by
 * Spring Cloud Gateway's default behaviour.
 *
 * Runs at ORDER = -180 (after trace headers are set).
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

                    var requestBuilder = exchange.getRequest().mutate();

                    String userId = jwt.getClaimAsString(CLAIM_USER_ID);
                    if (userId != null) requestBuilder.header(HEADER_USER_ID, userId);

                    String role = jwt.getClaimAsString(CLAIM_ROLE);
                    if (role != null) requestBuilder.header(HEADER_USER_ROLE, role);

                    String tenantId = jwt.getClaimAsString(CLAIM_TENANT_ID);
                    if (tenantId != null) requestBuilder.header(HEADER_TENANT_ID, tenantId);

                    String sub = jwt.getSubject();
                    if (sub != null) requestBuilder.header(HEADER_AUTH_SUB, sub);

                    log.debug("JWT relay: userId={} role={} tenantId={}", userId, role, tenantId);

                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                // Unauthenticated exchanges (public paths) pass through without headers
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return -180;
    }
}
